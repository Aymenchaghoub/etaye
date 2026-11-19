package dev.aymen.etaye.records.service;

import dev.aymen.etaye.records.domain.Patient;
import dev.aymen.etaye.records.domain.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class PatientService {

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public Patient createPatient(UUID practiceId, String firstName, String lastName) {
        Patient patient = new Patient(practiceId, firstName, lastName);
        return patientRepository.save(patient);
    }

    @Transactional(readOnly = true)
    public Optional<Patient> getPatient(UUID practiceId, UUID id) {
        // Defense in depth: application predicate combined with database RLS
        return patientRepository.findByIdAndPracticeId(id, practiceId);
    }

    @Transactional(readOnly = true)
    public Optional<Patient> getPatientWithoutAppPredicate(UUID id) {
        // Direct ID lookup: relies exclusively on database RLS
        return patientRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Patient> listPatients(UUID practiceId) {
        return patientRepository.findAllByPracticeId(practiceId);
    }

    @Transactional(readOnly = true)
    public List<Patient> listPatientsWithoutAppPredicate() {
        return patientRepository.findAll();
    }
}
