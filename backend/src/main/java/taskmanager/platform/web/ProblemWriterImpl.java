package taskmanager.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import taskmanager.platform.ApiProblemException;
import taskmanager.platform.ProblemWriter;
import tools.jackson.databind.json.JsonMapper;

/** Same shape as {@link ProblemResponses}, for callers that run before or outside Spring MVC. */
@Component
class ProblemWriterImpl implements ProblemWriter {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public void write(HttpServletRequest request, HttpServletResponse response, ApiProblemException problem) throws IOException {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.valueOf(problem.status()));
        Problems.normalize(detail, Problems.correlationId(request), problem.code(), problem.violations());
        Map<String, Object> body = new LinkedHashMap<>();
        if (detail.getType() != null && !"about:blank".equals(detail.getType().toString())) {
            body.put("type", detail.getType().toString());
        }
        body.put("title", detail.getTitle() != null ? detail.getTitle() : HttpStatus.valueOf(problem.status()).getReasonPhrase());
        body.put("status", detail.getStatus());
        body.put("instance", detail.getInstance().toString());
        if (detail.getProperties() != null) {
            body.putAll(detail.getProperties());
        }
        response.setStatus(problem.status());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemHeaders.apply(problem, response::setHeader);
        response.getOutputStream().write(JSON.writeValueAsBytes(body));
    }
}
