package taskmanager.appearance.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.appearance.internal.Appearance;
import taskmanager.appearance.internal.AppearanceService;
import taskmanager.appearance.internal.Logo;
import taskmanager.identity.AccessContext;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.AdministrationRights;
import taskmanager.platform.ApiProblemException;

/**
 * Name, accent color and logo of the company. Reading the whole setting needs a complete login; changing needs the right
 * {@code appearance.manage} (TASK-040). The service checks it authoritatively in the transaction of the change; the endpoint
 * refuses a request that clearly lacks it before it reads a body. The ETag is the version of the setting and every change
 * needs it as If-Match.
 *
 * <p>Below {@code /public} the login page, the password change and the page after logging out read only what they show:
 * name, accent color and logo, without version, time or person (TASK-081). The authentication filter lets exactly these
 * two reads through without a session.
 */
@RestController
@RequestMapping("/api/v1/appearance")
class AppearanceController {
    static final String LOGO_PATH = "/api/v1/appearance/logo";
    static final String PUBLIC_LOGO_PATH = "/api/v1/appearance/public/logo";

    private final AppearanceService appearance;
    private final AccessContexts contexts;
    private final ObjectProvider<AdministrationRights> rights;

    AppearanceController(AppearanceService appearance, AccessContexts contexts, ObjectProvider<AdministrationRights> rights) {
        this.appearance = appearance;
        this.contexts = contexts;
        this.rights = rights;
    }

    record LogoBody(String contentType, int size, String url) {
    }

    record AppearanceBody(String name, String defaultName, String accentColor, String defaultAccentColor, LogoBody logo, long version,
            Instant updatedAt) {
    }

    record PublicBody(String name, String accentColor, String logoUrl) {
    }

    record ChangeRequest(String accentColor, String name) {
    }

    @GetMapping
    ResponseEntity<AppearanceBody> read() {
        reader();
        return body(appearance.current());
    }

    @PutMapping
    ResponseEntity<AppearanceBody> change(@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) ChangeRequest request) {
        AccessContext actor = administrator();
        return body(appearance.change(actor, expectedVersion(ifMatch), request == null ? null : request.accentColor(),
            request == null ? null : request.name()));
    }

    @DeleteMapping
    ResponseEntity<AppearanceBody> reset(@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        AccessContext actor = administrator();
        return body(appearance.reset(actor, expectedVersion(ifMatch)));
    }

    @GetMapping("/public")
    PublicBody publicAppearance() {
        Appearance current = appearance.current();
        return new PublicBody(current.name(), current.accentColor(), current.hasLogo() ? PUBLIC_LOGO_PATH : null);
    }

    @GetMapping("/public/logo")
    ResponseEntity<byte[]> publicLogo() {
        return image();
    }

    @GetMapping("/logo")
    ResponseEntity<byte[]> logo() {
        reader();
        return image();
    }

    private ResponseEntity<byte[]> image() {
        var logo = appearance.logo().orElseThrow(() -> ApiProblemException.of(404, "appearance.logo_not_found"));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, logo.type())
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "default-src 'none'; sandbox")
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"logo\"")
            .body(logo.content());
    }

    @PutMapping("/logo")
    ResponseEntity<AppearanceBody> replaceLogo(@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            HttpServletRequest request) throws IOException {
        AccessContext actor = administrator();
        long expected = expectedVersion(ifMatch);
        if (request.getContentLengthLong() > Logo.MAX_BYTES) {
            throw ApiProblemException.of(413, "request.too_large");
        }
        byte[] content;
        try (InputStream in = request.getInputStream()) {
            content = in.readNBytes(Logo.MAX_BYTES + 1);
        }
        return body(appearance.replaceLogo(actor, expected, request.getContentType(), content));
    }

    @DeleteMapping("/logo")
    ResponseEntity<AppearanceBody> removeLogo(@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        AccessContext actor = administrator();
        return body(appearance.removeLogo(actor, expectedVersion(ifMatch)));
    }

    private AccessContext reader() {
        AccessContext context = contexts.require();
        if (context.level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        return context;
    }

    /** A snapshot of the right: it spares an unauthorized request the work, the service decides. */
    private AccessContext administrator() {
        AccessContext context = reader();
        var port = AdministrationRights.unique(rights);
        if (port == null || !port.held().contains(AdministrationRight.APPEARANCE_MANAGE)) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        return context;
    }

    private static long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw ApiProblemException.of(428, "appearance.precondition_required");
        }
        String value = ifMatch.trim();
        if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            throw ApiProblemException.of(412, "appearance.version_conflict");
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException e) {
            throw ApiProblemException.of(412, "appearance.version_conflict");
        }
    }

    private static ResponseEntity<AppearanceBody> body(Appearance current) {
        LogoBody logo = current.hasLogo()
            ? new LogoBody(current.logoType(), current.logoSize(), LOGO_PATH + "?v=" + current.version())
            : null;
        return ResponseEntity.ok()
            .eTag("\"" + current.version() + "\"")
            .body(new AppearanceBody(current.name(), AppearanceService.DEFAULT_NAME, current.accentColor(), AppearanceService.DEFAULT_ACCENT, logo, current.version(), current.updatedAt()));
    }
}
