package taskmanager.platform.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Common error handling: every error from a controller becomes {@code application/problem+json}
 * (RFC 9457) with a stable product {@code code} and the request's {@code correlationId}.
 * Framework texts in {@code detail} are removed and {@code instance} is replaced because both can
 * echo input values (see {@link Problems}). Modules add their own codes later through a coordinated
 * contract change.
 */
@RestControllerAdvice
class ProblemResponses extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ProblemResponses.class);

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception exception, WebRequest request) {
        log.error("Unexpected error", exception);
        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        return createResponseEntity(body, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            Problems.normalize(problem, request.getAttribute(CorrelationIdFilter.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
        }
        return super.createResponseEntity(body, headers, status, request);
    }
}
