package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.identity.dto.AuthenticatedAccount;
import dev.aymen.etaye.identity.dto.MembershipGrant;
import dev.aymen.etaye.infrastructure.error.ResourceNotFoundException;
import dev.aymen.etaye.infrastructure.security.AuthorizationDenial.Reason;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
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
        UUID actorAccountId = account.account().id();
        MembershipGrant membership = account.getMembershipForPractice(practiceId).orElse(null);
        if (membership == null) {
            // Only the actor's own memberships can establish an audit scope for an invisible practice.
            List<MembershipGrant> activeMemberships = account.memberships().stream()
                    .filter(MembershipGrant::isActive)
                    .limit(2)
                    .toList();
            UUID auditPracticeId = activeMemberships.size() == 1 ? activeMemberships.getFirst().practiceId() : null;
            throw new PracticeNotVisibleException(new AuthorizationDenial(actorAccountId, auditPracticeId,
                    Reason.NOT_VISIBLE, null));
        }
        if (!membership.isActive()) {
            throw new CapabilityDeniedException(new AuthorizationDenial(actorAccountId, practiceId,
                    Reason.MEMBERSHIP_NOT_ACTIVE, capability));
        }
        if (!membership.hasCapability(capability)) {
            throw new CapabilityDeniedException(new AuthorizationDenial(actorAccountId, practiceId,
                    Reason.CAPABILITY_MISSING, capability));
        }
        return account;
    }

    public static final class CapabilityDeniedException extends AccessDeniedException {
        private final AuthorizationDenial denial;

        private CapabilityDeniedException(AuthorizationDenial denial) {
            super("Access denied");
            this.denial = denial;
        }

        public AuthorizationDenial denial() {
            return denial;
        }
    }

    public static final class PracticeNotVisibleException extends ResourceNotFoundException {
        private final AuthorizationDenial denial;

        private PracticeNotVisibleException(AuthorizationDenial denial) {
            super("Resource not found");
            this.denial = denial;
        }

        public AuthorizationDenial denial() {
            return denial;
        }
    }
}
