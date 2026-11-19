package dev.aymen.etaye.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolved identity and current practice membership grants.
 * Re-evaluated on every request to guarantee that suspended accounts
 * or revoked memberships take immediate effect without waiting for session expiry.
 */
public record AuthenticatedAccount(
        AccountProfile account,
        List<MembershipGrant> memberships
) {
    public Optional<MembershipGrant> getMembershipForPractice(UUID practiceId) {
        if (!account.isActive()) {
            return Optional.empty();
        }
        return memberships.stream()
                .filter(m -> m.practiceId().equals(practiceId))
                .findFirst();
    }

    public Optional<MembershipGrant> getActiveMembershipForPractice(UUID practiceId) {
        return getMembershipForPractice(practiceId)
                .filter(MembershipGrant::isActive);
    }

    public boolean hasCapabilityInPractice(UUID practiceId, String capability) {
        return getActiveMembershipForPractice(practiceId)
                .map(m -> m.hasCapability(capability))
                .orElse(false);
    }
}
