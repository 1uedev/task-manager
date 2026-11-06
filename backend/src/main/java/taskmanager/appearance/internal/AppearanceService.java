package taskmanager.appearance.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.AccessContext;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.AdministrationRights;
import taskmanager.platform.ApiProblemException;

/**
 * Reads and changes the single appearance row. Every change needs the right {@code appearance.manage}, checked first in
 * the transaction of the change (TASK-040), and the version the client last saw, so two administrators cannot overwrite each
 * other unnoticed, and every change is written to the audit trail.
 */
@Service
public class AppearanceService {
    public static final String DEFAULT_ACCENT = "#f97316";
    public static final String DEFAULT_NAME = "Task Manager";
    public static final int NAME_MAX = 80;
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    /*
     * The rule of the name (TASK-087, finding F1 of docs/REVIEW_TASK-081_PR-243.md), written out character by character so
     * the server, the database check of V0054 and the interface (frontend/src/api/client.ts) agree on every Unicode version.
     * EDGE: spaces removed at both ends, every Unicode space including the protected ones and the line breaks. FORBIDDEN:
     * control characters, line and paragraph separators and the format characters (Unicode 15.1, category Cf), nowhere.
     */
    static final String EDGE = "\\x{9}-\\x{d}\\x{1c}-\\x{20}\\x{85}\\x{a0}\\x{1680}\\x{2000}-\\x{200a}\\x{2028}\\x{2029}\\x{202f}\\x{205f}\\x{3000}\\x{feff}";
    static final String FORBIDDEN = "\\x{0}-\\x{1f}\\x{7f}-\\x{9f}\\x{ad}\\x{600}-\\x{605}\\x{61c}\\x{6dd}\\x{70f}\\x{890}\\x{891}\\x{8e2}\\x{180e}"
        + "\\x{200b}-\\x{200f}\\x{2028}-\\x{202e}\\x{2060}-\\x{2064}\\x{2066}-\\x{206f}\\x{feff}\\x{fff9}-\\x{fffb}\\x{110bd}\\x{110cd}"
        + "\\x{13430}-\\x{1343f}\\x{1bca0}-\\x{1bca3}\\x{1d173}-\\x{1d17a}\\x{e0001}\\x{e0020}-\\x{e007f}";
    private static final Pattern EDGES = Pattern.compile("^[" + EDGE + "]+|[" + EDGE + "]+$");
    private static final Pattern FORBIDDEN_CHARACTER = Pattern.compile("[" + FORBIDDEN + "]");

    private final JdbcClient jdbc;
    private final Clock clock;
    private final ObjectProvider<AdministrationRights> rights;

    AppearanceService(JdbcClient jdbc, Clock clock, ObjectProvider<AdministrationRights> rights) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.rights = rights;
    }

    public record LogoContent(String type, byte[] content) {
    }

    public Appearance current() {
        return jdbc.sql("select name, accent_color, logo_type, coalesce(octet_length(logo), 0) as logo_size, version, updated_at from appearance_setting where id = 1")
            .query((rs, row) -> new Appearance(rs.getString("name"), rs.getString("accent_color"), rs.getString("logo_type"), rs.getInt("logo_size"),
                rs.getLong("version"), rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant()))
            .single();
    }

    public Optional<LogoContent> logo() {
        return jdbc.sql("select logo_type, logo from appearance_setting where id = 1 and logo is not null")
            .query((rs, row) -> new LogoContent(rs.getString("logo_type"), rs.getBytes("logo"))).optional();
    }

    /**
     * Changes the accent color, the name or both in one step (TASK-081). A part that is not sent stays as it is; a request
     * that sends neither is refused. Each part that really changes gets its own entry in the audit trail; a request that
     * changes nothing leaves version and audit as they are (TASK-087, hint H2 of docs/REVIEW_TASK-081_PR-243.md).
     */
    @Transactional
    public Appearance change(AccessContext actor, long expectedVersion, String accentColor, String name) {
        requireRight();
        if (accentColor == null && name == null) {
            throw ApiProblemException.of(400, "appearance.change_empty");
        }
        String color = accentColor == null ? null : validColor(accentColor);
        String shown = name == null ? null : validName(name);
        Appearance before = locked(expectedVersion);
        String nextColor = color == null ? before.accentColor() : color;
        String nextName = shown == null ? before.name() : shown;
        boolean colorChanged = !nextColor.equals(before.accentColor());
        boolean nameChanged = !nextName.equals(before.name());
        if (!colorChanged && !nameChanged) {
            return before;
        }
        jdbc.sql("update appearance_setting set accent_color = ?, name = ?, version = version + 1, updated_at = ?, updated_by = ? where id = 1")
            .params(nextColor, nextName, ts(), actor.accountId()).update();
        if (colorChanged) {
            audit(actor, "appearance.accent_changed", before.accentColor() + " -> " + nextColor);
        }
        if (nameChanged) {
            audit(actor, "appearance.name_changed", before.name() + " -> " + nextName);
        }
        return current();
    }

    /**
     * Puts name, accent color and logo back to the defaults in one step (TASK-081). When everything already is the
     * default, version and audit stay as they are (TASK-087, finding R2 of docs/REVIEW_TASK-087_PR-259.md).
     */
    @Transactional
    public Appearance reset(AccessContext actor, long expectedVersion) {
        requireRight();
        Appearance before = locked(expectedVersion);
        if (before.name().equals(DEFAULT_NAME) && before.accentColor().equals(DEFAULT_ACCENT) && !before.hasLogo()) {
            return before;
        }
        jdbc.sql("update appearance_setting set name = ?, accent_color = ?, logo = null, logo_type = null, version = version + 1, updated_at = ?, updated_by = ? where id = 1")
            .params(DEFAULT_NAME, DEFAULT_ACCENT, ts(), actor.accountId()).update();
        audit(actor, "appearance.reset", null);
        return current();
    }

    /** The accent color in lower case, or 400 {@code appearance.color_invalid}. */
    static String validColor(String raw) {
        if (!COLOR.matcher(raw).matches()) {
            throw ApiProblemException.of(400, "appearance.color_invalid");
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    /**
     * The name after the spaces of {@link #EDGE} are removed at both ends: 1 to 80 characters, none of {@link #FORBIDDEN}
     * and no half of a surrogate pair, so it fits a page head, a window title and a mail header alike and the interface
     * accepts what the server stored (TASK-087).
     */
    static String validName(String raw) {
        String name = EDGES.matcher(raw).replaceAll("");
        if (name.isEmpty() || name.codePointCount(0, name.length()) > NAME_MAX || FORBIDDEN_CHARACTER.matcher(name).find()
            || name.codePoints().anyMatch(c -> c >= 0xd800 && c <= 0xdfff)) {
            throw ApiProblemException.of(400, "appearance.name_invalid");
        }
        return name;
    }

    @Transactional
    public Appearance replaceLogo(AccessContext actor, long expectedVersion, String declaredType, byte[] content) {
        requireRight();
        if (content.length == 0) {
            throw ApiProblemException.of(400, "appearance.logo_empty");
        }
        if (content.length > Logo.MAX_BYTES) {
            throw ApiProblemException.of(413, "request.too_large");
        }
        String type = Logo.detectType(content).orElseThrow(() -> ApiProblemException.of(415, "appearance.logo_type_invalid"));
        if (declaredType == null || !type.equals(declaredType.toLowerCase(Locale.ROOT).split(";")[0].trim())) {
            throw ApiProblemException.of(415, "appearance.logo_type_invalid");
        }
        // The right start of a file is not yet an image anyone can see (TASK-043).
        if (Logo.validType(content).isEmpty()) {
            throw ApiProblemException.of(415, "appearance.logo_unreadable");
        }
        Appearance before = locked(expectedVersion);
        // The same file again changes nothing: version and audit stay (TASK-087, finding R2).
        if (before.hasLogo() && logo().filter(l -> l.type().equals(type) && Arrays.equals(l.content(), content)).isPresent()) {
            return before;
        }
        jdbc.sql("update appearance_setting set logo = ?, logo_type = ?, version = version + 1, updated_at = ?, updated_by = ? where id = 1")
            .params(content, type, ts(), actor.accountId()).update();
        audit(actor, "appearance.logo_replaced", type + ", " + content.length + " bytes");
        return current();
    }

    @Transactional
    public Appearance removeLogo(AccessContext actor, long expectedVersion) {
        requireRight();
        Appearance before = locked(expectedVersion);
        // Without a logo there is nothing to remove: version and audit stay (TASK-087, finding R2).
        if (!before.hasLogo()) {
            return before;
        }
        jdbc.sql("update appearance_setting set logo = null, logo_type = null, version = version + 1, updated_at = ?, updated_by = ? where id = 1")
            .params(ts(), actor.accountId()).update();
        audit(actor, "appearance.logo_removed", null);
        return current();
    }

    /** Before anything else of the change: the rights lock and the identity locks of the requester come first. */
    private void requireRight() {
        var port = AdministrationRights.unique(rights);
        if (port == null) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        port.require(AdministrationRight.APPEARANCE_MANAGE);
    }

    /** Locks the row and checks the version the client last saw. */
    private Appearance locked(long expectedVersion) {
        long version = jdbc.sql("select version from appearance_setting where id = 1 for update").query(Long.class).single();
        if (version != expectedVersion) {
            throw ApiProblemException.of(412, "appearance.version_conflict");
        }
        return current();
    }

    private void audit(AccessContext actor, String event, String detail) {
        jdbc.sql("insert into appearance_audit_event (occurred_at, actor_account_id, actor, event, detail) values (?, ?, ?, ?, ?)")
            .params(ts(), actor.accountId(), actor.username(), event, detail).update();
    }

    private java.time.OffsetDateTime ts() {
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        return java.time.OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC);
    }
}
