package taskmanager.identity.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.identity.session.SessionService;
import taskmanager.identity.session.SessionService.Resolved;
import taskmanager.identity.session.Tokens;
import taskmanager.platform.ApiProblemException;
import taskmanager.platform.ProblemWriter;

/**
 * Default deny for the whole API. Every request below {@code /api/} needs a valid session, except the session
 * endpoint itself (state, login, logout). A session whose password change is still pending may only change the
 * password. Server side expiry, deactivation and revocation are decided here for every request, so a stale
 * browser tab and a direct API call with the cookie are treated alike. Unsafe methods additionally need a CSRF
 * token (see {@link #csrfProblem}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class AuthenticationFilter extends OncePerRequestFilter {
    static final String RESOLVED = AuthenticationFilter.class.getName() + ".resolved";
    private static final String SESSION_PATH = "/api/v1/session";
    private static final String PASSWORD_PATH = "/api/v1/session/password";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final SessionService sessions;
    private final ProblemWriter problems;

    AuthenticationFilter(SessionService sessions, ProblemWriter problems) {
        this.sessions = sessions;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = canonicalPath(request);
        if (!path.equals("/api") && !path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        String token = Cookies.read(request, Cookies.SESSION);
        Optional<Resolved> resolved = token == null ? Optional.empty() : sessions.resolve(token);
        resolved.ifPresent(value -> request.setAttribute(RESOLVED, value));

        ApiProblemException denial = authorizationProblem(request.getMethod(), path, token != null, resolved);
        boolean staleCookie = denial != null && "auth.session_expired".equals(denial.code());
        if (denial == null && !SAFE_METHODS.contains(request.getMethod())) {
            denial = csrfProblem(request, resolved);
        }
        if (denial != null) {
            if (staleCookie) {
                response.addHeader("Set-Cookie", Cookies.expired(Cookies.SESSION));
            }
            problems.write(request, response, denial);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * The path the way Spring MVC matches it: the segments of the raw request path with path parameters
     * ({@code /api;x/...}) removed and percent encoding decoded ({@code /%61pi/...}), empty segments dropped.
     * The decision must not rest on the raw request URI, because the server routes every one of these spellings
     * to the same handler. Spring's own parsing of the request path is used so that both always agree.
     */
    static String canonicalPath(HttpServletRequest request) {
        StringBuilder path = new StringBuilder();
        for (PathContainer.Element element : ServletRequestPathUtils.parseAndCache(request).pathWithinApplication().elements()) {
            if (element instanceof PathContainer.PathSegment segment && !segment.valueToMatch().isEmpty()) {
                path.append('/').append(segment.valueToMatch());
            }
        }
        return path.length() == 0 ? "/" : path.toString();
    }

    private static ApiProblemException authorizationProblem(String method, String path, boolean hadToken, Optional<Resolved> resolved) {
        if (path.equals(SESSION_PATH)) {
            return null;
        }
        if (resolved.isEmpty()) {
            return ApiProblemException.of(401, hadToken ? "auth.session_expired" : "auth.required");
        }
        boolean pending = resolved.get().view().level() == AuthenticationLevel.PASSWORD_CHANGE_REQUIRED;
        if (pending && !(method.equals("POST") && path.equals(PASSWORD_PATH))) {
            return ApiProblemException.of(403, "auth.password_change_required");
        }
        return null;
    }

    /**
     * Token in a header that equals the CSRF cookie, bound to the session when there is one, and an
     * {@code Origin} (if sent) that names this host. Without a session (login) the cookie comparison and the
     * origin check apply.
     */
    private static ApiProblemException csrfProblem(HttpServletRequest request, Optional<Resolved> resolved) {
        ApiProblemException refusal = ApiProblemException.of(403, "auth.csrf_invalid");
        String origin = request.getHeader("Origin");
        if (origin != null && !originMatches(origin, request.getHeader("Host"))) {
            return refusal;
        }
        String header = request.getHeader(Cookies.CSRF_HEADER);
        String cookie = Cookies.read(request, Cookies.CSRF);
        if (header == null || cookie == null || !Tokens.sameValue(header, cookie)) {
            return refusal;
        }
        if (resolved.isPresent() && !Tokens.sameValue(Tokens.hash(header), resolved.get().csrfHash())) {
            return refusal;
        }
        return null;
    }

    private static boolean originMatches(String origin, String host) {
        if (host == null) {
            return false;
        }
        try {
            URI uri = URI.create(origin);
            String authority = uri.getRawAuthority();
            return authority != null && authority.equalsIgnoreCase(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

}
