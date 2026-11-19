package dev.aymen.etaye.infrastructure.security;

import tools.jackson.databind.ObjectMapper;
import dev.aymen.etaye.infrastructure.error.ApiProblem;
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

    private final ObjectMapper objectMapper;

    public ProblemAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
