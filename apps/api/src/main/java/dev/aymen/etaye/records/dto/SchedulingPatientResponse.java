package dev.aymen.etaye.records.dto;

import dev.aymen.etaye.records.domain.PatientView;

import java.time.LocalDate;
import java.util.UUID;

/** Minimal scheduling projection: identity and contact only, no clinical content or portal linkage. */
public record SchedulingPatientResponse(
        UUID id,
        UUID practiceId,
        UUID recordId,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String contactEmail,
        String phone
) {
    public static SchedulingPatientResponse of(PatientView view) {
        return new SchedulingPatientResponse(view.id(), view.practiceId(), view.recordId(), view.firstName(),
                view.lastName(), view.dateOfBirth(), view.contactEmail(), view.phone());
    }
}
