package dev.aymen.etaye.infrastructure.demo;

import dev.aymen.etaye.infrastructure.error.ApiProblem;
import dev.aymen.etaye.infrastructure.error.ProblemResponseWriter;
import dev.aymen.etaye.infrastructure.security.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@ConditionalOnProperty(name = "etaye.demo.enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class DemoRequestBarrier extends OncePerRequestFilter {

    private final DemoFixture fixture;
    private final ProblemResponseWriter problems;

    public DemoRequestBarrier(DemoFixture fixture, ProblemResponseWriter problems) {
        this.fixture = fixture;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Use the decoded servlet path so admission follows the container's routing.
        String path = request.getServletPath();
        return !path.startsWith("/api/v1/") || path.startsWith("/api/v1/probes/")
                || (DemoFixture.RESET_PATH.equals(path) && "POST".equals(request.getMethod()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!fixture.tryEnter()) {
            problems.writeProblem(response, ApiProblem.of(503, "Service Unavailable", "DEMO_RESET_IN_PROGRESS",
                    "The synthetic fixture is being restored", CorrelationIdFilter.getCorrelationId(request)));
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            fixture.leave();
        }
    }
}
