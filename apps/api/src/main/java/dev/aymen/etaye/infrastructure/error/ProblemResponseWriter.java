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

    public void writeProblem(HttpServletResponse response, ApiProblem problem) throws IOException {
        response.setStatus(problem.status());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
