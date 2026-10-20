package taskmanager.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.identity.session.SessionService;
import taskmanager.identity.session.SessionService.Issued;
import taskmanager.identity.session.SessionService.Resolved;
import taskmanager.identity.session.SessionService.SessionView;
import taskmanager.identity.session.Tokens;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.AdministrationRights;
import taskmanager.platform.ApiProblemException;

/** State of the own session, login, logout and the password change of the logged in person. */
@RestController
@RequestMapping("/api/v1/session")
class SessionController {
    private final SessionService sessions;
    private final Clock clock;
    private final ObjectProvider<AdministrationRights> rights;

    SessionController(SessionService sessions, Clock clock, ObjectProvider<AdministrationRights> rights) {
        this.sessions = sessions;
        this.clock = clock;
        this.rights = rights;
    }

    record LoginRequest(String username, String password) {
    }

    record PasswordChangeRequest(String currentPassword, String newPassword) {
    }

    /**
     * @param authenticated false for an anonymous visitor
     * @param level {@code full} or {@code password_change_required}; null when anonymous
     * @param rights keys of the administrative rights the session holds, sorted; only in the answer to the state of an
     *        authenticated request, empty while the login is incomplete. A hint for what an interface shows, never a permission
     * @param csrfToken only present when the server just issued a token (anonymous visit, login, password change)
     */
    record SessionBody(boolean authenticated, String username, String displayName, String level,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<String> rights,
            Instant expiresAt, Instant passwordChangeBy, String csrfToken) {
        static SessionBody anonymous(String csrfToken) {
            return new SessionBody(false, null, null, null, null, null, null, csrfToken);
        }

        static SessionBody of(SessionView view, List<String> rights, String csrfToken) {
            String level = view.level() == AuthenticationLevel.FULL ? "full" : "password_change_required";
            return new SessionBody(true, view.username(), view.displayName(), level, rights, view.expiresAt(),
                view.restrictedUntil(), csrfToken);
        }
    }

    @GetMapping
    SessionBody state(HttpServletRequest request, HttpServletResponse response) {
        Resolved resolved = (Resolved) request.getAttribute(AuthenticationFilter.RESOLVED);
        if (resolved != null) {
            return SessionBody.of(resolved.view(), heldRights(), null);
        }
        String existing = Cookies.read(request, Cookies.CSRF);
        if (existing != null) {
            return SessionBody.anonymous(null);
        }
        String csrf = Tokens.random();
        response.addHeader(HttpHeaders.SET_COOKIE, Cookies.csrf(csrf, Duration.ofHours(12)));
        return SessionBody.anonymous(csrf);
    }

    private List<String> heldRights() {
        var port = AdministrationRights.unique(rights);
        return port == null ? List.of() : port.held().stream().map(AdministrationRight::key).sorted().toList();
    }

    @PostMapping
    SessionBody login(@RequestBody(required = false) LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        if (body == null || body.username() == null || body.password() == null) {
            throw ApiProblemException.withViolations(400, "request.invalid",
                body == null || body.username() == null ? List.of("username.required") : List.of("password.required"));
        }
        Issued issued = sessions.login(body.username(), body.password(), request.getRemoteAddr());
        Resolved previous = (Resolved) request.getAttribute(AuthenticationFilter.RESOLVED);
        if (previous != null) {
            sessions.logout(previous.sessionId());
        }
        return issue(issued, response);
    }

    @DeleteMapping
    org.springframework.http.ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Resolved resolved = (Resolved) request.getAttribute(AuthenticationFilter.RESOLVED);
        if (resolved != null) {
            sessions.logout(resolved.sessionId());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, Cookies.expired(Cookies.SESSION));
        response.addHeader(HttpHeaders.SET_COOKIE, Cookies.expired(Cookies.CSRF));
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @PostMapping("/password")
    SessionBody changePassword(@RequestBody(required = false) PasswordChangeRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        if (body == null || body.currentPassword() == null || body.newPassword() == null) {
            throw ApiProblemException.withViolations(400, "request.invalid",
                body == null || body.currentPassword() == null ? List.of("currentPassword.required") : List.of("newPassword.required"));
        }
        Resolved resolved = (Resolved) request.getAttribute(AuthenticationFilter.RESOLVED);
        Issued issued = sessions.changePassword(resolved.sessionId(), body.currentPassword(), body.newPassword(), request.getRemoteAddr());
        return issue(issued, response);
    }

    private SessionBody issue(Issued issued, HttpServletResponse response) {
        Duration lifetime = Duration.between(clock.instant(), issued.view().expiresAt());
        response.addHeader(HttpHeaders.SET_COOKIE, Cookies.session(issued.sessionToken(), lifetime));
        response.addHeader(HttpHeaders.SET_COOKIE, Cookies.csrf(issued.csrfToken(), lifetime));
        return SessionBody.of(issued.view(), null, issued.csrfToken());
    }
}
