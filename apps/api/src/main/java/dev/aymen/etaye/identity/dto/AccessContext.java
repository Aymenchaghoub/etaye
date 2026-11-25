package dev.aymen.etaye.identity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Display metadata for a grant resolved within the current account's identity scope. The IANA time zone of the practice
 * (D-64) is present for a membership and absent for a portal link; it shows instants and never authorizes anything.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccessContext(Kind kind, UUID grantId, UUID practiceId, String practiceName, DisplayRole displayRole,
                            String timezone) {

    public enum Kind { MEMBERSHIP, PORTAL }

    public enum DisplayRole { CLINICAL, ADMINISTRATION, SCHEDULING, TEAM, PATIENT }
}
