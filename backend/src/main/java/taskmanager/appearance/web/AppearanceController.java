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
 * Accent color and logo of the company. Reading needs a complete login; changing needs the right {@code appearance.manage}
 * (TASK-040). The service checks it authoritatively in the transaction of the change; the endpoint refuses a request that
 * clearly lacks it before it reads a body. The ETag is the version of the setting and every change needs it as If-Match.
 */
@RestController
@RequestMapping("/api/v1/appearance")
class AppearanceController {
    static final String LOGO_PATH = "/api/v1/appearance/logo";

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

    record AppearanceBody(String accentColor, String defaultAccentColor, LogoBody logo, long version, Instant updatedAt) {
    }

    record AccentRequest(String accentColor) {
    }

    @GetMapping
    ResponseEntity<AppearanceBody> read() {
        reader();
        return body(appearance.current());
    }

    @PutMapping
    ResponseEntity<AppearanceBody> changeAccent(@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) AccentRequest request) {
        AccessContext actor = administrator();
        return body(appearance.changeAccent(actor, expectedVersion(ifMatch), request == null ? null : request.accentColor()));
    }

    @GetMapping("/logo")
    ResponseEntity<byte[]> logo() {
        reader();
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
        var port = rights.getIfUnique();
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
            .body(new AppearanceBody(current.accentColor(), AppearanceService.DEFAULT_ACCENT, logo, current.version(), current.updatedAt()));
    }
}
