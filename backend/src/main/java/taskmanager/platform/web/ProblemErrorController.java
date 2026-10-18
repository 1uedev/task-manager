package taskmanager.platform.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's default error response for errors that never reach a controller, for
 * example {@code sendError} calls or failures in filters. It produces the same problem shape as
 * {@link ProblemResponses} instead of the default body, which contains the request path.
 */
@RestController
@RequestMapping("${server.error.path:${error.path:/error}}")
class ProblemErrorController implements ErrorController {
    @RequestMapping
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        HttpStatus status = status(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
        ProblemDetail problem = Problems.normalize(ProblemDetail.forStatus(status), Problems.correlationId(request));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static HttpStatus status(Object code) {
        if (code instanceof Integer value) {
            HttpStatus status = HttpStatus.resolve(value);
            if (status != null && status.isError()) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
