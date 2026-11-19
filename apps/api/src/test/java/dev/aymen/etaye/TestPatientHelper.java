package dev.aymen.etaye;

import dev.aymen.etaye.records.domain.Patient;
import dev.aymen.etaye.records.domain.PatientRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Test helper providing access to patient repository queries and test-only
 * patient entity creation for isolation and adversarial tests.
 */
@Component
public class TestPatientHelper {

    private final PatientRepository patientRepository;

    public TestPatientHelper(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public Patient createPatient(UUID practiceId, String firstName, String lastName) {
        Patient patient = new Patient(practiceId, firstName, lastName, LocalDate.of(1980, 1, 1), null, null);
        return patientRepository.save(patient);
    }

    public Optional<Patient> getPatient(UUID practiceId, UUID id) {
        return patientRepository.findByIdAndPracticeId(id, practiceId);
    }

    public Optional<Patient> getPatientWithoutAppPredicate(UUID id) {
        return patientRepository.findById(id);
    }

    public List<Patient> listPatients(UUID practiceId) {
        return patientRepository.findAllByPracticeId(practiceId);
    }

    public List<Patient> listPatientsWithoutAppPredicate() {
        return patientRepository.findAll();
    }
}
