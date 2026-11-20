package dev.aymen.etaye.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

final class ExistingSessionCsrfFilter extends OncePerRequestFilter {

    private final ProblemAccessDeniedHandler accessDeniedHandler;
    private final BrowserSessionLifetime lifetime;

    ExistingSessionCsrfFilter(ProblemAccessDeniedHandler accessDeniedHandler, BrowserSessionLifetime lifetime) {
        this.accessDeniedHandler = accessDeniedHandler;
        this.lifetime = lifetime;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Issuing through CsrfFilter on an arbitrary unsafe request would bypass /csrf's issuance budget.
        if (CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)) {
            HttpSession session = request.getSession(false);
            if (session == null || lifetime.isExpiredAnonymous(session)) {
                if (session != null) {
                    session.invalidate();
                }
                accessDeniedHandler.handle(request, response, new MissingCsrfTokenException(null));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
