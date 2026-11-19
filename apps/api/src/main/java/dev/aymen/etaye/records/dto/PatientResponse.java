package dev.aymen.etaye.records.dto;

import dev.aymen.etaye.records.domain.Patient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        UUID practiceId,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String contactEmail,
        String phone,
        UUID recordId
) {
    public static PatientResponse of(Patient patient, UUID recordId) {
        return new PatientResponse(
                patient.getId(),
                patient.getVersion(),
                patient.getCreatedAt(),
                patient.getUpdatedAt(),
                patient.getPracticeId(),
                patient.getFirstName(),
                patient.getLastName(),
                patient.getDateOfBirth(),
                patient.getContactEmail(),
                patient.getPhone(),
                recordId
        );
    }
}
