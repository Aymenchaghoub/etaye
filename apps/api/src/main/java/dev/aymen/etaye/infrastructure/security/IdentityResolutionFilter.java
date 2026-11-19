package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.identity.domain.AuthenticatedAccount;
import dev.aymen.etaye.identity.repository.IdentityLookupRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Re-resolves account state and practice memberships on every request via etaye_identity.
 * Ensures account suspension or capability revocation takes immediate effect
 * on active sessions without waiting for cookie expiry.
 */
public class IdentityResolutionFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATED_ACCOUNT_ATTR = "authenticatedAccount";

    private final IdentityLookupRepository identityLookupRepository;
    private final ProblemAccessDeniedHandler accessDeniedHandler;

    public IdentityResolutionFilter(IdentityLookupRepository identityLookupRepository,
                                    ProblemAccessDeniedHandler accessDeniedHandler) {
        this.identityLookupRepository = identityLookupRepository;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication auth = context.getAuthentication();

        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
            String principalStr = auth.getPrincipal().toString();
            try {
                UUID accountId = UUID.fromString(principalStr);
                Optional<AuthenticatedAccount> resolved = identityLookupRepository.resolveAccountAndMemberships(accountId);

                if (resolved.isEmpty() || !resolved.get().account().isActive()) {
                    // Account suspended or revoked: clear security context and reject immediately
                    SecurityContextHolder.clearContext();
                    accessDeniedHandler.handle(request, response,
                            new AccessDeniedException("Account is inactive or does not exist"));
                    return;
                }

                // Store resolved account with current memberships in request attribute
                request.setAttribute(AUTHENTICATED_ACCOUNT_ATTR, resolved.get());

            } catch (IllegalArgumentException e) {
                // Not a valid UUID principal: allow chain to proceed or fail as appropriate
            }
        }

        filterChain.doFilter(request, response);
    }
}
