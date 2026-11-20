package dev.aymen.etaye.records.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByIdAndPracticeId(UUID id, UUID practiceId);

    List<Patient> findAllByPracticeId(UUID practiceId);
}
