package dev.aymen.etaye.identity.dto;

import java.util.UUID;

/**
 * Limited projection of the global Account entity.
 * Contains only identity attributes required for request resolution.
 * In accordance with ADR-003, password_hash is never present in this projection.
 */
public record AccountProfile(
        UUID id,
        String normalizedEmail,
        String displayName,
        String state,
        String locale
) {
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(state);
    }
}
