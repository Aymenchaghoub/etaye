package dev.aymen.etaye.identity.dto;

import java.util.Set;
import java.util.UUID;

/**
 * Limited projection of the Membership entity.
 * Represents an account's granted role/capabilities within a specific practice.
 */
public record MembershipGrant(
        UUID id,
        UUID accountId,
        UUID practiceId,
        String state,
        Set<String> capabilities
) {
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(state);
    }

    public boolean hasCapability(String capability) {
        return isActive() && capabilities.contains(capability);
    }
}
