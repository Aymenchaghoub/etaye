package fr.etaye.patients.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    @Query("SELECT p FROM Patient p WHERE p.practiceId = :practiceId")
    List<Patient> findAllByPracticeId(@Param("practiceId") UUID practiceId);

    @Query("SELECT p FROM Patient p WHERE p.id = :id AND p.practiceId = :practiceId")
    Optional<Patient> findByIdAndPracticeId(@Param("id") UUID id, @Param("practiceId") UUID practiceId);
}
