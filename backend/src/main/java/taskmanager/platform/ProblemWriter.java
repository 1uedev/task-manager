package taskmanager.platform;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Writes an {@link ApiProblemException} as the common problem response for code that runs outside of
 * controllers, for example servlet filters. Inside controllers throw the exception instead.
 */
public interface ProblemWriter {
    void write(HttpServletRequest request, HttpServletResponse response, ApiProblemException problem) throws IOException;
}
