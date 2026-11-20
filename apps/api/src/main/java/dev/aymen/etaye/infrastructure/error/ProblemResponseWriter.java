package dev.aymen.etaye.infrastructure.error;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared serializer component for application/problem+json used by security filters
 * and exception entry points.
 */
@Component
public class ProblemResponseWriter {

    private static final List<String> RETAINED_SECURITY_HEADERS = List.of(
            "Strict-Transport-Security", "X-Content-Type-Options", "X-Frame-Options", "X-XSS-Protection",
            "Referrer-Policy", "Content-Security-Policy", "Permissions-Policy", "Access-Control-Allow-Origin", "Vary");

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
        // reset() clears the stale headers of a half-built success response (Content-Length, ETag, Set-Cookie)
        // Preserve the validated CORS decision and the other headers already set by the security chain.
        Map<String, List<String>> retained = new LinkedHashMap<>();
        for (String name : RETAINED_SECURITY_HEADERS) {
            List<String> values = List.copyOf(response.getHeaders(name));
            if (!values.isEmpty()) {
                retained.put(name, values);
            }
        }
        response.reset();
        retained.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        if (!retained.containsKey("X-Content-Type-Options")) {
            response.setHeader("X-Content-Type-Options", "nosniff");
        }
        response.setHeader("Cache-Control", "no-store");
        response.setStatus(problem.status());
        response.setHeader("Content-Type", "application/problem+json");
        response.setCharacterEncoding("UTF-8");
        if (problem.correlationId() != null && !problem.correlationId().isBlank()) {
            response.setHeader("X-Correlation-Id", problem.correlationId());
        }
        byte[] body = objectMapper.writeValueAsBytes(problem);
        try {
            response.getOutputStream().write(body);
        } catch (IllegalStateException writerAlreadyChosen) {
            // The container remembers that getWriter() was called earlier on this response
            response.getWriter().write(new String(body, java.nio.charset.StandardCharsets.UTF_8));
        }
        return true;
    }
}
