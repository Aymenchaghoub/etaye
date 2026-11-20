package dev.aymen.etaye.infrastructure.security;

import java.util.UUID;

public record AuthorizationDenial(UUID actorAccountId, UUID auditPracticeId, Reason reason, String capability) {

    public enum Reason {
        MEMBERSHIP_NOT_ACTIVE,
        CAPABILITY_MISSING,
        NOT_VISIBLE
    }
}
