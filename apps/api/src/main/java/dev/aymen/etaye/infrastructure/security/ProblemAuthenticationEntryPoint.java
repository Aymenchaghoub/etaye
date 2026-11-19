package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.infrastructure.error.ApiProblem;
import dev.aymen.etaye.infrastructure.error.ProblemResponseWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemResponseWriter problemResponseWriter;

    public ProblemAuthenticationEntryPoint(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        ApiProblem problem = ApiProblem.of(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "UNAUTHORIZED",
                "Authentication required or session expired",
                correlationId
        );
        problemResponseWriter.writeProblem(response, problem);
    }
}
