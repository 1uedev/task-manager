package taskmanager.platform.web;

import java.util.function.BiConsumer;
import taskmanager.platform.ApiProblemException;

/** Response headers that belong to a deliberate product error. */
final class ProblemHeaders {
    /** Sent with every 401. The scheme is a product specific cookie session, not Basic, so browsers show no password dialog. */
    static final String CHALLENGE = "Session realm=\"task-manager\"";

    private ProblemHeaders() {
    }

    static void apply(ApiProblemException problem, BiConsumer<String, String> header) {
        if (problem.status() == 401) {
            header.accept("WWW-Authenticate", CHALLENGE);
        }
        if (problem.retryAfterSeconds() != null) {
            header.accept("Retry-After", Integer.toString(problem.retryAfterSeconds()));
        }
    }
}
