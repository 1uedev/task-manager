package taskmanager.appearance.internal;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.appearance.MailTemplates;
import taskmanager.identity.AccessContext;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.AdministrationRights;
import taskmanager.platform.ApiProblemException;

/**
 * The mail template "Antwort an Kunden" (TASK-082, DEC-112): greeting, signature and footer in German and English and the
 * address of the application that fills {@code {{portal.url}}}. One row; a language whose texts are not stored uses the
 * built-in template. Reading and changing need the right {@code appearance.manage}, checked first in the transaction; every
 * change needs the version the client last saw and is written to the audit trail of the appearance. Every stored text is
 * checked when it is saved, so rendering a mail never fails.
 */
@Service
public class MailTemplateService implements MailTemplates {
    static final List<String> LANGUAGES = List.of(GERMAN, ENGLISH);
    private static final int ADDRESS_MAX = 2000;

    private final JdbcClient jdbc;
    private final Clock clock;
    private final ObjectProvider<AdministrationRights> rights;
    private final AppearanceService appearance;

    MailTemplateService(JdbcClient jdbc, Clock clock, ObjectProvider<AdministrationRights> rights, AppearanceService appearance) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.rights = rights;
        this.appearance = appearance;
    }

    /** The template of one language and whether it is stored or the built-in one. */
    public record Language(String code, MailLayout.Texts texts, boolean stored) {
        public String greeting() {
            return texts.greeting();
        }

        public String signature() {
            return texts.signature();
        }

        public String footer() {
            return texts.footer();
        }
    }

    /** The whole template as the page shows it. */
    public record Template(String applicationUrl, List<Language> languages, long version, Instant updatedAt) {
        Language language(String code) {
            return languages.stream().filter(l -> l.code().equals(code)).findFirst().orElse(languages.get(0));
        }
    }

    /** The texts a request sends for one language. */
    public record Draft(String greeting, String signature, String footer) {
    }

    public static List<String> placeholders() {
        return MailLayout.PLACEHOLDERS;
    }

    public static Language builtIn(String code) {
        return new Language(code, MailLayout.builtIn(code), false);
    }

    /** The template for the page; needs {@code appearance.manage}. */
    @Transactional
    public Template read() {
        requireRight();
        return current();
    }

    Template current() {
        return jdbc.sql("select * from appearance_mail_template where id = 1").query((rs, row) -> {
            List<Language> languages = new ArrayList<>();
            for (String code : LANGUAGES) {
                String greeting = rs.getString("greeting_" + code);
                languages.add(greeting == null ? builtIn(code)
                    : new Language(code, new MailLayout.Texts(greeting, rs.getString("signature_" + code), rs.getString("footer_" + code)), true));
            }
            return new Template(rs.getString("application_url"), List.copyOf(languages), rs.getLong("version"),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
        }).single();
    }

    /**
     * Stores the texts of both languages and the address of the application. A text may be empty; an unknown placeholder, a
     * text over 2 000 characters or with control characters other than line breaks and tabs, and an address that is not
     * https are refused with 400 and the part named.
     */
    @Transactional
    public Template change(AccessContext actor, long expectedVersion, String rawAddress, Draft german, Draft english) {
        requireRight();
        if (german == null || english == null) {
            throw ApiProblemException.of(400, "mail_template.languages_missing");
        }
        String address = address(rawAddress);
        Map<String, MailLayout.Texts> texts = Map.of(GERMAN, texts(GERMAN, german), ENGLISH, texts(ENGLISH, english));
        Template before = locked(expectedVersion);
        jdbc.sql("""
                update appearance_mail_template set application_url = ?, greeting_de = ?, signature_de = ?, footer_de = ?,
                    greeting_en = ?, signature_en = ?, footer_en = ?, version = version + 1, updated_at = ?, updated_by = ? where id = 1
                """).params(address, texts.get(GERMAN).greeting(), texts.get(GERMAN).signature(), texts.get(GERMAN).footer(),
                texts.get(ENGLISH).greeting(), texts.get(ENGLISH).signature(), texts.get(ENGLISH).footer(), ts(), actor.accountId()).update();
        for (String code : LANGUAGES) {
            if (!before.language(code).stored() || !before.language(code).texts().equals(texts.get(code))) {
                audit(actor, "mail_template.changed", code);
            }
        }
        if (!java.util.Objects.equals(before.applicationUrl(), address)) {
            audit(actor, "mail_template.address_changed", (before.applicationUrl() == null ? "-" : before.applicationUrl()) + " -> "
                + (address == null ? "-" : address));
        }
        return current();
    }

    /** Back to the built-in template in both languages; the address of the application stays. */
    @Transactional
    public Template reset(AccessContext actor, long expectedVersion) {
        requireRight();
        locked(expectedVersion);
        jdbc.sql("""
                update appearance_mail_template set greeting_de = null, signature_de = null, footer_de = null, greeting_en = null,
                    signature_en = null, footer_en = null, version = version + 1, updated_at = ?, updated_by = ? where id = 1
                """).params(ts(), actor.accountId()).update();
        audit(actor, "mail_template.reset", null);
        return current();
    }

    /**
     * The sample mail of the page with the texts, the address, the name and the color not yet saved and synthetic values only,
     * so the page shows nothing of a ticket or customer. Needs {@code appearance.manage}; the draft is checked as for saving.
     */
    @Transactional
    public RenderedMail preview(String language, String rawAddress, Draft draft, String name, String accentColor) {
        requireRight();
        if (!GERMAN.equals(language) && !ENGLISH.equals(language)) {
            throw ApiProblemException.of(400, "mail_template.languages_missing");
        }
        String code = language;
        if (draft == null) {
            throw ApiProblemException.of(400, "mail_template.languages_missing");
        }
        MailLayout.Texts texts = texts(code, draft);
        String address = address(rawAddress);
        Appearance current = appearance.current();
        String shownName = name == null ? current.name() : AppearanceService.validName(name);
        String color = accentColor == null ? current.accentColor() : AppearanceService.validColor(accentColor);
        boolean english = ENGLISH.equals(code);
        ReplyValues sample = english
            ? new ReplyValues("Alex Example", "HELP-42", "Printer in hall 2 does not start", "Sam Support", "support@example.com")
            : new ReplyValues("Erika Beispiel", "HELP-42", "Drucker in Halle 2 startet nicht", "Max Support", "support@example.com");
        String body = english
            ? "Thank you for your message.\nWe have restarted the printer; it works again.\nPlease let us know if anything is still wrong."
            : "Vielen Dank für Ihre Nachricht.\nWir haben den Drucker neu gestartet, er läuft wieder.\nBitte melden Sie sich, falls noch etwas nicht stimmt.";
        return render(code, texts, address, shownName, color, current.hasLogo() ? appearance.logo().orElse(null) : null, sample, body);
    }

    @Override
    @Transactional
    public RenderedMail reply(String language, ReplyValues values, String body) {
        String code = ENGLISH.equals(language) ? ENGLISH : GERMAN;
        Template template = current();
        Appearance current = appearance.current();
        return render(code, template.language(code).texts(), template.applicationUrl(), current.name(), current.accentColor(),
            current.hasLogo() ? appearance.logo().orElse(null) : null, values, body);
    }

    private static RenderedMail render(String code, MailLayout.Texts texts, String address, String name, String color,
            AppearanceService.LogoContent logo, ReplyValues values, String body) {
        Map<String, String> filled = new HashMap<>();
        filled.put(MailLayout.COMPANY, name);
        filled.put(MailLayout.CONTACT, values.contactName());
        filled.put(MailLayout.NUMBER, values.ticketNumber());
        filled.put(MailLayout.TITLE, values.ticketTitle());
        filled.put(MailLayout.SENDER, values.senderName());
        filled.put(MailLayout.MAILBOX, values.mailboxAddress());
        filled.put(MailLayout.PORTAL, address == null ? "" : address);
        filled.replaceAll((key, value) -> value == null ? "" : value);
        Parts parts = new Parts(name, color, logo != null, MailLayout.fill(texts.greeting(), filled, false), MailLayout.body(body, false),
            MailLayout.fill(texts.signature(), filled, false), MailLayout.fill(texts.footer(), filled, false));
        String html = MailLayout.html(code, name, color, logo != null, MailLayout.fill(texts.greeting(), filled, true), MailLayout.body(body, true),
            MailLayout.fill(texts.signature(), filled, true), MailLayout.fill(texts.footer(), filled, true));
        return new RenderedMail(code, MailLayout.text(parts), html, logo == null ? null : new Image(logo.type(), logo.content()), parts);
    }

    /** The three parts of one language, checked; the violations name the language and the part. */
    private static MailLayout.Texts texts(String code, Draft draft) {
        List<String> invalid = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        String greeting = part(code, "greeting", draft.greeting(), invalid, unknown);
        String signature = part(code, "signature", draft.signature(), invalid, unknown);
        String footer = part(code, "footer", draft.footer(), invalid, unknown);
        if (!invalid.isEmpty()) {
            throw ApiProblemException.withViolations(400, "mail_template.text_invalid", invalid);
        }
        if (!unknown.isEmpty()) {
            throw ApiProblemException.withViolations(400, "mail_template.placeholder_unknown", unknown);
        }
        return new MailLayout.Texts(greeting, signature, footer);
    }

    private static String part(String code, String name, String raw, List<String> invalid, List<String> unknown) {
        String text = raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n');
        if (text.codePointCount(0, text.length()) > MailLayout.PART_MAX
            || text.codePoints().anyMatch(c -> c != '\n' && c != '\t' && (Character.isISOControl(c) || c == 0x2028 || c == 0x2029))) {
            invalid.add(code + "." + name);
            return text;
        }
        MailLayout.unknownPlaceholders(text).forEach(token -> unknown.add(code + "." + name + ": " + token));
        return text;
    }

    /** The address of the application: empty is none, otherwise an absolute https address with a host and nothing hidden in it. */
    static String address(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String address = raw.strip();
        if (address.length() > ADDRESS_MAX || address.codePoints().anyMatch(c -> c <= 0x20 || c >= 0x7f)) {
            throw ApiProblemException.of(400, "mail_template.address_invalid");
        }
        try {
            URI uri = new URI(address);
            if (!"https".equals(uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT)) || uri.getHost() == null
                || uri.getHost().isEmpty() || uri.getRawUserInfo() != null || uri.isOpaque()) {
                throw ApiProblemException.of(400, "mail_template.address_invalid");
            }
            // The scheme is case insensitive; it is stored in lower case so that the database check and every mail agree.
            return "https" + address.substring(uri.getScheme().length());
        } catch (URISyntaxException invalid) {
            throw ApiProblemException.of(400, "mail_template.address_invalid");
        }
    }

    private void requireRight() {
        var port = AdministrationRights.unique(rights);
        if (port == null) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        port.require(AdministrationRight.APPEARANCE_MANAGE);
    }

    private Template locked(long expectedVersion) {
        long version = jdbc.sql("select version from appearance_mail_template where id = 1 for update").query(Long.class).single();
        if (version != expectedVersion) {
            throw ApiProblemException.of(412, "mail_template.version_conflict");
        }
        return current();
    }

    private void audit(AccessContext actor, String event, String detail) {
        jdbc.sql("insert into appearance_audit_event (occurred_at, actor_account_id, actor, event, detail) values (?, ?, ?, ?, ?)")
            .params(ts(), actor.accountId(), actor.username(), event, detail).update();
    }

    private OffsetDateTime ts() {
        return OffsetDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }
}
