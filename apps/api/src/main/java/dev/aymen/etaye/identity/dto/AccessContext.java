package dev.aymen.etaye.identity.dto;

import java.util.UUID;

/** Display metadata for a grant resolved within the current account's identity scope. */
public record AccessContext(Kind kind, UUID grantId, UUID practiceId, String practiceName, DisplayRole displayRole) {

    public enum Kind { MEMBERSHIP, PORTAL }

    public enum DisplayRole { CLINICAL, ADMINISTRATION, SCHEDULING, TEAM, PATIENT }
}
