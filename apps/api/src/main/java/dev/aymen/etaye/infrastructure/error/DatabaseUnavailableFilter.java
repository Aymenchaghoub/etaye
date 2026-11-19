package dev.aymen.etaye.infrastructure.error;

import dev.aymen.etaye.infrastructure.security.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter ordered right after CorrelationIdFilter and BEFORE the Spring
 * Session filter. Catches database connection failures thrown by the rest of
 * the chain and writes a 503 application/problem+json response.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class DatabaseUnavailableFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUnavailableFilter.class);

    private final ProblemResponseWriter problemResponseWriter;

    public DatabaseUnavailableFilter(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/v1/probes/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            if (DatabaseUnavailable.isConnectionFailure(ex)) {
                String correlationId = CorrelationIdFilter.getCorrelationId(request);
                log.error("Database connection failure: class={}, code={}, correlationId={}",
                        ex.getClass().getName(), "SERVICE_UNAVAILABLE", correlationId);
                ApiProblem problem = ApiProblem.of(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "Service Unavailable",
                        "SERVICE_UNAVAILABLE",
                        "Service temporarily unavailable",
                        correlationId
                );
                problemResponseWriter.writeProblem(response, problem);
                return;
            }
            if (ex instanceof ServletException se) {
                throw se;
            }
            if (ex instanceof IOException ioe) {
                throw ioe;
            }
            throw new ServletException(ex);
        }
    }
}
