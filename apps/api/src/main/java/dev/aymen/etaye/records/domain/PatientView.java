package dev.aymen.etaye.records.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Read model of a practice-local patient with its record identifier and the normalized order keys. */
public record PatientView(
        UUID id,
        UUID practiceId,
        UUID recordId,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String contactEmail,
        String phone,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String lastNameKey,
        String firstNameKey
) {
}
