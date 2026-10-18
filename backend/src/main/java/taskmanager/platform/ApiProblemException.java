package taskmanager.platform;

import java.util.List;

/**
 * A deliberate, client visible error with a stable product {@code code}. Modules throw it instead of
 * building responses; the platform turns it into the common {@code application/problem+json} shape.
 * It carries no stack trace and only server defined text, so nothing from the request can leak into
 * the response through it.
 */
public final class ApiProblemException extends RuntimeException {
    private final int status;
    private final String code;
    private final List<String> violations;
    private final Integer retryAfterSeconds;

    private ApiProblemException(int status, String code, List<String> violations, Integer retryAfterSeconds) {
        super(code, null, false, false);
        this.status = status;
        this.code = code;
        this.violations = List.copyOf(violations);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiProblemException of(int status, String code) {
        return new ApiProblemException(status, code, List.of(), null);
    }

    /** Stable machine readable reasons, for example the violated password rules. */
    public static ApiProblemException withViolations(int status, String code, List<String> violations) {
        return new ApiProblemException(status, code, violations, null);
    }

    public static ApiProblemException retryAfter(int status, String code, int seconds) {
        return new ApiProblemException(status, code, List.of(), seconds);
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<String> violations() {
        return violations;
    }

    /** Seconds after which a retry makes sense, or {@code null}. */
    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
