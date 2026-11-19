package dev.aymen.etaye.infrastructure.security;

import tools.jackson.databind.ObjectMapper;
import dev.aymen.etaye.infrastructure.error.ApiProblem;
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

    private final ObjectMapper objectMapper;

    public ProblemAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        String detail = accessDeniedException.getMessage() != null && !accessDeniedException.getMessage().isBlank()
                ? accessDeniedException.getMessage()
                : "Access denied";

        ApiProblem problem = ApiProblem.of(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "FORBIDDEN",
                detail,
                correlationId
        );

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
