package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.identity.domain.AuthenticatedAccount;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PracticeSecurityService {

    public AuthenticatedAccount getAuthenticatedAccount(HttpServletRequest request) {
        AuthenticatedAccount account = (AuthenticatedAccount) request.getAttribute(IdentityResolutionFilter.AUTHENTICATED_ACCOUNT_ATTR);
        if (account == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return account;
    }

    public AuthenticatedAccount requireCapability(HttpServletRequest request, UUID practiceId, String capability) {
        AuthenticatedAccount account = getAuthenticatedAccount(request);
        if (!account.hasCapabilityInPractice(practiceId, capability)) {
            throw new AccessDeniedException("Missing required capability " + capability + " for practice " + practiceId);
        }
        return account;
    }
}
