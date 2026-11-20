package dev.aymen.etaye.infrastructure.error;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Shared serializer component for application/problem+json used by security filters
 * and exception entry points.
 */
@Component
public class ProblemResponseWriter {

    private final ObjectMapper objectMapper;

    public ProblemResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Writes an RFC 9457 problem JSON to the response.
     * If the response is already committed, does not write anything and returns false
     * so the caller can rethrow the original exception.
     * Otherwise resets the buffer, sets status, required headers (Content-Type and X-Correlation-Id),
     * serializes the problem JSON, and returns true.
     */
    public boolean writeProblem(HttpServletResponse response, ApiProblem problem) throws IOException {
        if (response.isCommitted()) {
            return false;
        }
        response.resetBuffer();
        response.setStatus(problem.status());
        response.setHeader("Content-Type", "application/problem+json");
        response.setCharacterEncoding("UTF-8");
        if (problem.correlationId() != null && !problem.correlationId().isBlank()) {
            response.setHeader("X-Correlation-Id", problem.correlationId());
        }
        objectMapper.writeValue(response.getWriter(), problem);
        return true;
    }
}
