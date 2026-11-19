package dev.aymen.etaye.records.dto;

import dev.aymen.etaye.records.domain.Patient;
import dev.aymen.etaye.records.domain.PatientRecord;

import java.time.Instant;
import java.util.UUID;

public record RecordResponse(
        UUID id,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        UUID practiceId,
        UUID patientId,
        Integer recordRevision,
        PatientResponse patient
) {
    public static RecordResponse of(PatientRecord record, Patient patient) {
        return new RecordResponse(
                record.getId(),
                record.getVersion(),
                record.getCreatedAt(),
                record.getUpdatedAt(),
                record.getPracticeId(),
                record.getPatientId(),
                record.getRecordRevision(),
                PatientResponse.of(patient, record.getId())
        );
    }
}
