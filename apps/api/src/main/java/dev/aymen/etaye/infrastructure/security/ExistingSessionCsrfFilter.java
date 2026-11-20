package dev.aymen.etaye.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

final class ExistingSessionCsrfFilter extends OncePerRequestFilter {

    private final ProblemAccessDeniedHandler accessDeniedHandler;

    ExistingSessionCsrfFilter(ProblemAccessDeniedHandler accessDeniedHandler) {
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Issuing through CsrfFilter on an arbitrary unsafe request would bypass /csrf's issuance budget.
        if (CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request) && request.getSession(false) == null) {
            accessDeniedHandler.handle(request, response, new MissingCsrfTokenException(null));
            return;
        }
        chain.doFilter(request, response);
    }
}
