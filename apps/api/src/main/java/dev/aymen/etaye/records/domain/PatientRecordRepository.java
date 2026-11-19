package dev.aymen.etaye.records.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientRecordRepository extends JpaRepository<PatientRecord, UUID> {

    Optional<PatientRecord> findByIdAndPracticeId(UUID id, UUID practiceId);

    Optional<PatientRecord> findByPatientIdAndPracticeId(UUID patientId, UUID practiceId);
}
