package taskmanager.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Browser protection for everything the server sends, including the built web interface that the image serves
 * from the same origin as the API (TASK-031). The interface loads its scripts and styles only from this origin
 * and may show a logo from this origin or from an https address. Built assets carry a content hash in their
 * name and may be cached for a long time; every other page is checked again on each visit, so a new image
 * takes effect on the next reload.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class WebInterfaceHeadersFilter extends OncePerRequestFilter {
    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self'; "
        + "img-src 'self' data: blob: https:; connect-src 'self'; font-src 'self'; object-src 'none'; "
        + "base-uri 'none'; form-action 'self'; frame-ancestors 'none'";
    private static final String ASSETS = "/assets/";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.equals("/api") && !path.startsWith("/api/") && !path.startsWith("/actuator")) {
            response.setHeader("Cache-Control", path.startsWith(ASSETS) ? "public, max-age=31536000, immutable" : "no-cache");
        }
        chain.doFilter(request, response);
    }
}
