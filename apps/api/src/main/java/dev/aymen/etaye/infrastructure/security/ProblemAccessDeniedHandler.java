package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.infrastructure.error.ApiProblem;
import dev.aymen.etaye.infrastructure.error.ProblemResponseWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemAccessDeniedHandler.class);
    private final ProblemResponseWriter problemResponseWriter;

    public ProblemAccessDeniedHandler(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        String correlationId = CorrelationIdFilter.getCorrelationId(request);
        boolean isCsrf = accessDeniedException instanceof CsrfException;
        String code = isCsrf ? "CSRF_REJECTED" : "FORBIDDEN";
        String detail = isCsrf ? "CSRF token is missing or invalid" : "Access denied";

        Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(accessDeniedException);
        String rootCauseClass = rootCause != null ? rootCause.getClass().getName() : accessDeniedException.getClass().getName();
        log.warn("Access denied: class={}, rootCauseClass={}, code={}, correlationId={}",
                accessDeniedException.getClass().getName(), rootCauseClass, code, correlationId);

        ApiProblem problem = ApiProblem.of(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                code,
                detail,
                correlationId
        );
        problemResponseWriter.writeProblem(response, problem);
    }
}
