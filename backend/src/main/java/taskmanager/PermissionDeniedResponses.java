package taskmanager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import taskmanager.permissions.PermissionDenied;
import taskmanager.platform.ApiProblemException;
import taskmanager.platform.ProblemWriter;

/**
 * A refusal of the permissions module that no controller translated, above all the check right before the commit, which
 * throws after the service method returned (TASK-043). It is answered like every other refusal, 403 {@code auth.forbidden},
 * and not as an internal error. Lives at the application entry point because the platform module does not know the
 * permissions module.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class PermissionDeniedResponses {
    private final ProblemWriter problems;

    PermissionDeniedResponses(ProblemWriter problems) {
        this.problems = problems;
    }

    @ExceptionHandler(PermissionDenied.class)
    void denied(HttpServletRequest request, HttpServletResponse response) throws IOException {
        problems.write(request, response, ApiProblemException.of(403, "auth.forbidden"));
    }
}
