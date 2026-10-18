package taskmanager.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ProblemDetail;

/**
 * Brings every problem response into the common shape. Only server-generated values are added:
 * {@code instance} is the request's correlation ID as a URN, so no part of the request path or
 * query is echoed back. Leaving {@code instance} empty is not enough because Spring MVC would
 * fill in the request URI.
 */
final class Problems {
    private Problems() {
    }

    static ProblemDetail normalize(ProblemDetail problem, Object correlationAttribute) {
        return normalize(problem, correlationAttribute, ProblemCode.forStatus(problem.getStatus()), List.of());
    }

    /** Variant for deliberate product errors: a module defined code and stable machine readable reasons. */
    static ProblemDetail normalize(ProblemDetail problem, Object correlationAttribute, String code, List<String> violations) {
        String correlationId = correlationAttribute instanceof String id ? id : UUID.randomUUID().toString();
        problem.setDetail(null);
        problem.setProperties(null);
        problem.setInstance(URI.create("urn:uuid:" + correlationId));
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId);
        if (!violations.isEmpty()) {
            problem.setProperty("violations", violations);
        }
        return problem;
    }

    static Object correlationId(HttpServletRequest request) {
        return request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
    }
}
