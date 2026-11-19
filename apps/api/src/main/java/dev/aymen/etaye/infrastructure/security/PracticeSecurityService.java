package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.identity.dto.AuthenticatedAccount;
import dev.aymen.etaye.identity.dto.MembershipGrant;
import dev.aymen.etaye.infrastructure.error.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class PracticeSecurityService {

    public AuthenticatedAccount getAuthenticatedAccount(HttpServletRequest request) {
        AuthenticatedAccount account = (AuthenticatedAccount) request.getAttribute(IdentityResolutionFilter.AUTHENTICATED_ACCOUNT_ATTR);
        if (account == null) {
            throw new AccessDeniedException("Access denied");
        }
        return account;
    }

    public AuthenticatedAccount requireCapability(HttpServletRequest request, UUID practiceId, String capability) {
        AuthenticatedAccount account = getAuthenticatedAccount(request);
        Optional<MembershipGrant> membership = account.getMembershipForPractice(practiceId);
        if (membership.isEmpty()) {
            // A practice the account has no membership in is NOT visible: answer 404 with generic detail
            throw new ResourceNotFoundException("Resource not found");
        }
        if (!membership.get().isActive()) {
            // A suspended membership is denied access: answer 403 with generic detail
            throw new AccessDeniedException("Access denied");
        }
        if (!membership.get().hasCapability(capability)) {
            // A member without the capability gets 403 with generic detail
            throw new AccessDeniedException("Access denied");
        }
        return account;
    }
}
