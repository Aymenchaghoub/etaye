package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.identity.dto.AuthenticatedAccount;
import dev.aymen.etaye.identity.service.IdentityLookupService;
import dev.aymen.etaye.infrastructure.error.ApiProblem;
import dev.aymen.etaye.infrastructure.error.DatabaseUnavailable;
import dev.aymen.etaye.infrastructure.error.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.SessionRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Re-resolves account state and practice memberships on every request via etaye_identity.
 * Ensures account suspension or capability revocation takes immediate effect
 * on active sessions without waiting for cookie expiry.
 * When account is inactive or missing, invalidates and deletes the persisted session,
 * and answers 401.
 * Excludes readiness probe endpoints from running session/identity filters.
 */
public class IdentityResolutionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdentityResolutionFilter.class);

    public static final String AUTHENTICATED_ACCOUNT_ATTR = "authenticatedAccount";

    private final IdentityLookupService identityLookupService;
    private final ProblemAuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemResponseWriter problemResponseWriter;
    private final SessionRepository<?> sessionRepository;

    public IdentityResolutionFilter(IdentityLookupService identityLookupService,
                                    ProblemAuthenticationEntryPoint authenticationEntryPoint,
                                    ProblemResponseWriter problemResponseWriter,
                                    SessionRepository<?> sessionRepository) {
        this.identityLookupService = identityLookupService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.problemResponseWriter = problemResponseWriter;
        this.sessionRepository = sessionRepository;
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
            SecurityContext context = SecurityContextHolder.getContext();
            Authentication auth = context.getAuthentication();

            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
                String principalStr = auth.getPrincipal().toString();
                UUID accountId;
                try {
                    accountId = UUID.fromString(principalStr);
                } catch (IllegalArgumentException e) {
                    // Not a valid UUID principal: allow chain to proceed
                    filterChain.doFilter(request, response);
                    return;
                }

                Optional<AuthenticatedAccount> resolved = identityLookupService.resolveAccountAndMemberships(accountId);

                if (resolved.isEmpty() || !resolved.get().account().isActive()) {
                    // Account suspended or revoked: delete persisted session, clear context, return 401
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        String sessionId = session.getId();
                        try {
                            session.invalidate();
                        } catch (Exception ex) {
                            log.warn("Session invalidation failed: class={}, code={}",
                                    ex.getClass().getName(), "SESSION_INVALIDATION_FAILED");
                        }
                        if (sessionRepository != null) {
                            try {
                                sessionRepository.deleteById(sessionId);
                            } catch (Exception ex) {
                                log.warn("Persisted session deletion failed: class={}, code={}",
                                        ex.getClass().getName(), "SESSION_DELETION_FAILED");
                            }
                        }
                    }
                    SecurityContextHolder.clearContext();
                    if (isCsrfEndpoint(request)) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    authenticationEntryPoint.commence(request, response,
                            new AuthenticationCredentialsNotFoundException("Account is inactive or does not exist"));
                    return;
                }

                // Store resolved account with current memberships in request attribute
                request.setAttribute(AUTHENTICATED_ACCOUNT_ATTR, resolved.get());
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            if (DatabaseUnavailable.isConnectionFailure(e)) {
                String correlationId = CorrelationIdFilter.getCorrelationId(request);
                log.error("Database connection failure in identity filter: class={}, code={}, correlationId={}",
                        e.getClass().getName(), "SERVICE_UNAVAILABLE", correlationId);
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
            if (e instanceof ServletException se) {
                throw se;
            }
            if (e instanceof IOException ioe) {
                throw ioe;
            }
            throw new ServletException(e);
        }
    }

    private boolean isCsrfEndpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;
        return "/api/v1/csrf".equals(path);
    }
}
