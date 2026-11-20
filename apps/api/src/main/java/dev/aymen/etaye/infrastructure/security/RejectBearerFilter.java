package dev.aymen.etaye.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.regex.Pattern;

final class RejectBearerFilter extends OncePerRequestFilter {

    private static final Pattern BEARER = Pattern.compile("^Bearer(?:\\s|$)", Pattern.CASE_INSENSITIVE);
    private final AccessDeniedHandler accessDeniedHandler;

    RejectBearerFilter(AccessDeniedHandler accessDeniedHandler) {
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        // The browser stack cannot safely combine credentials. Native bearer sessions are introduced separately.
        boolean bearer = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION)).stream()
                .anyMatch(value -> BEARER.matcher(value.stripLeading()).find());
        if (bearer) {
            accessDeniedHandler.handle(request, response, new AccessDeniedException("Access denied"));
            return;
        }
        chain.doFilter(request, response);
    }
}
