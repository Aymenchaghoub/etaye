package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.infrastructure.error.ApiProblem;
import dev.aymen.etaye.infrastructure.error.ProblemResponseWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemResponseWriter problemResponseWriter;

    public ProblemAccessDeniedHandler(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "FORBIDDEN",
                "Access denied",
                correlationId
        );
        problemResponseWriter.writeProblem(response, problem);
    }
}
