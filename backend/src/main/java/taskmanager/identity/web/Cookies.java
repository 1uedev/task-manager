package taskmanager.identity.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * The two cookies of the web interface. The {@code __Host-} prefix makes browsers accept them only with
 * {@code Secure}, path {@code /} and no {@code Domain}. The session cookie is {@code HttpOnly}; the CSRF cookie
 * must be readable by the interface so it can send the value in a header. Production runs behind TLS.
 */
final class Cookies {
    static final String SESSION = "__Host-tm-session";
    static final String CSRF = "__Host-tm-csrf";
    static final String CSRF_HEADER = "X-CSRF-Token";

    private Cookies() {
    }

    static String session(String token, Duration maxAge) {
        return build(SESSION, token, true, maxAge);
    }

    static String csrf(String token, Duration maxAge) {
        return build(CSRF, token, false, maxAge);
    }

    static String expired(String name) {
        return build(name, "", !CSRF.equals(name), Duration.ZERO);
    }

    private static String build(String name, String value, boolean httpOnly, Duration maxAge) {
        return ResponseCookie.from(name, value).httpOnly(httpOnly).secure(true).sameSite("Lax").path("/")
            .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build().toString();
    }

    static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName()) && !cookie.getValue().isEmpty()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
