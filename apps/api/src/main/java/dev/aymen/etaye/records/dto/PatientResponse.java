package dev.aymen.etaye.records.dto;

import dev.aymen.etaye.records.domain.Patient;
import dev.aymen.etaye.records.domain.PatientView;

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

    public static PatientResponse of(PatientView view) {
        return new PatientResponse(view.id(), view.version(), view.createdAt(), view.updatedAt(), view.practiceId(),
                view.firstName(), view.lastName(), view.dateOfBirth(), view.contactEmail(), view.phone(), view.recordId());
    }
}
