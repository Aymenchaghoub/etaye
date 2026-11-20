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
import org.springframework.http.HttpStatus;
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
    private final BrowserSessionLifetime lifetime;

    public IdentityResolutionFilter(IdentityLookupService identityLookupService,
                                    ProblemAuthenticationEntryPoint authenticationEntryPoint,
                                    ProblemResponseWriter problemResponseWriter,
                                    SessionRepository<?> sessionRepository, BrowserSessionLifetime lifetime) {
        this.identityLookupService = identityLookupService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.problemResponseWriter = problemResponseWriter;
        this.sessionRepository = sessionRepository;
        this.lifetime = lifetime;
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

                HttpSession session = request.getSession(false);
                if (resolved.isEmpty() || !resolved.get().account().isActive()
                        || !lifetime.isCurrent(session, resolved.get().account().credentialRevision())) {

                    if (isCsrfEndpoint(request)) {
                        if (session != null) {
                            try {
                                // invalidate() already deletes the persisted session through the repository
                                session.invalidate();
                            } catch (Exception ex) {
                                if (!DatabaseUnavailable.isConnectionFailure(ex)) {
                                    // A programming or unexpected error is not an outage: let the outermost filter answer 500
                                    rethrow(ex);
                                }
                                log.error("Failed to delete revoked session: class={}, code={}",
                                        ex.getClass().getName(), "SESSION_DELETION_FAILED");
                                String correlationId = CorrelationIdFilter.getCorrelationId(request);
                                ApiProblem problem = ApiProblem.of(
                                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                                        "Service Unavailable",
                                        "SERVICE_UNAVAILABLE",
                                        "Service temporarily unavailable",
                                        correlationId
                                );
                                boolean written = problemResponseWriter.writeProblem(response, problem);
                                if (!written) {
                                    rethrow(ex);
                                }
                                return;
                            }
                        }
                        SecurityContextHolder.clearContext();
                        filterChain.doFilter(request, response);
                        return;
                    }

                    // Every other path keeps its 401
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
                    authenticationEntryPoint.commence(request, response,
                            new AuthenticationCredentialsNotFoundException("Account is inactive or does not exist"));
                    return;
                }

                lifetime.recordActivity(request, session);
                // Store resolved account with current memberships in request attribute
                request.setAttribute(AUTHENTICATED_ACCOUNT_ATTR, resolved.get());
            } else {
                lifetime.retireExpiredAnonymousSession(request);
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
                boolean written = problemResponseWriter.writeProblem(response, problem);
                if (!written) {
                    rethrow(e);
                }
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

    private void rethrow(Exception ex) throws ServletException, IOException {
        if (ex instanceof ServletException se) {
            throw se;
        }
        if (ex instanceof IOException ioe) {
            throw ioe;
        }
        throw new ServletException(ex);
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
