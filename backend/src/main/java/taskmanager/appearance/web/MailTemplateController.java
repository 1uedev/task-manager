package taskmanager.appearance.web;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.appearance.MailTemplates;
import taskmanager.appearance.internal.MailTemplateService;
import taskmanager.identity.AccessContext;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.AdministrationRights;
import taskmanager.platform.ApiProblemException;

/**
 * The mail template "Antwort an Kunden" (TASK-082, DEC-112). Every endpoint needs a complete login and the right
 * {@code appearance.manage}; the service checks the right authoritatively. The ETag is the version of the template and a
 * change needs it as If-Match. The preview fills the unsaved texts with synthetic values only.
 */
@RestController
@RequestMapping("/api/v1/appearance/mail-template")
class MailTemplateController {
    private final MailTemplateService templates;
    private final AccessContexts contexts;
    private final ObjectProvider<AdministrationRights> rights;

    MailTemplateController(MailTemplateService templates, AccessContexts contexts, ObjectProvider<AdministrationRights> rights) {
        this.templates = templates;
        this.contexts = contexts;
        this.rights = rights;
    }

    record TextsBody(String greeting, String signature, String footer) {
    }

    record LanguageBody(String greeting, String signature, String footer, boolean stored, TextsBody builtIn) {
    }

    record TemplateBody(String applicationUrl, LanguageBody de, LanguageBody en, List<String> placeholders, long version, Instant updatedAt) {
    }

    record ChangeRequest(String applicationUrl, TextsBody de, TextsBody en) {
    }

    record PreviewRequest(String language, String applicationUrl, TextsBody texts, String name, String accentColor) {
    }

    record PartsBody(String name, String accentColor, boolean logo, String greeting, String body, String signature, String footer) {
        static PartsBody of(MailTemplates.Parts p) {
            return new PartsBody(p.name(), p.accentColor(), p.logo(), p.greeting(), p.body(), p.signature(), p.footer());
        }
    }

    record PreviewBody(String language, String text, String html, PartsBody parts) {
        static PreviewBody of(MailTemplates.RenderedMail mail) {
            return new PreviewBody(mail.language(), mail.text(), mail.html(), PartsBody.of(mail.parts()));
        }
    }

    @GetMapping
    ResponseEntity<TemplateBody> read() {
        administrator();
        return body(templates.read());
    }

    @PutMapping
    ResponseEntity<TemplateBody> change(@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) ChangeRequest request) {
        AccessContext actor = administrator();
        long version = expectedVersion(ifMatch);
        if (request == null) {
            throw ApiProblemException.of(400, "mail_template.languages_missing");
        }
        return body(templates.change(actor, version, request.applicationUrl(), draft(request.de()), draft(request.en())));
    }

    @DeleteMapping
    ResponseEntity<TemplateBody> reset(@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        AccessContext actor = administrator();
        return body(templates.reset(actor, expectedVersion(ifMatch)));
    }

    @PostMapping("/preview")
    PreviewBody preview(@RequestBody(required = false) PreviewRequest request) {
        administrator();
        if (request == null) {
            throw ApiProblemException.of(400, "mail_template.languages_missing");
        }
        return PreviewBody.of(templates.preview(request.language(), request.applicationUrl(), draft(request.texts()), request.name(),
            request.accentColor()));
    }

    private static MailTemplateService.Draft draft(TextsBody texts) {
        return texts == null ? null : new MailTemplateService.Draft(texts.greeting(), texts.signature(), texts.footer());
    }

    /** A snapshot of the right: it spares an unauthorized request the work, the service decides. */
    private AccessContext administrator() {
        AccessContext context = contexts.require();
        if (context.level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        var port = AdministrationRights.unique(rights);
        if (port == null || !port.held().contains(AdministrationRight.APPEARANCE_MANAGE)) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        return context;
    }

    private static long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw ApiProblemException.of(428, "mail_template.precondition_required");
        }
        String value = ifMatch.trim();
        if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            throw ApiProblemException.of(412, "mail_template.version_conflict");
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException e) {
            throw ApiProblemException.of(412, "mail_template.version_conflict");
        }
    }

    private static ResponseEntity<TemplateBody> body(MailTemplateService.Template template) {
        return ResponseEntity.ok().eTag("\"" + template.version() + "\"").body(new TemplateBody(template.applicationUrl(),
            language(template, MailTemplates.GERMAN), language(template, MailTemplates.ENGLISH), MailTemplateService.placeholders(),
            template.version(), template.updatedAt()));
    }

    private static LanguageBody language(MailTemplateService.Template template, String code) {
        MailTemplateService.Language language = template.languages().stream().filter(l -> l.code().equals(code)).findFirst().orElseThrow();
        MailTemplateService.Language builtIn = MailTemplateService.builtIn(code);
        return new LanguageBody(language.greeting(), language.signature(), language.footer(), language.stored(),
            new TextsBody(builtIn.greeting(), builtIn.signature(), builtIn.footer()));
    }
}
