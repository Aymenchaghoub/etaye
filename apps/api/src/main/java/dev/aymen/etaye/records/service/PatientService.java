package dev.aymen.etaye.records.service;

import dev.aymen.etaye.audit.service.AuditService;
import dev.aymen.etaye.communications.service.OutboxService;
import dev.aymen.etaye.infrastructure.error.ResourceNotFoundException;
import dev.aymen.etaye.infrastructure.idempotency.IdempotencyService;
import dev.aymen.etaye.infrastructure.transaction.PracticeTransaction;
import dev.aymen.etaye.records.domain.Patient;
import dev.aymen.etaye.records.domain.PatientRecord;
import dev.aymen.etaye.records.domain.PatientRecordRepository;
import dev.aymen.etaye.records.domain.PatientRepository;
import dev.aymen.etaye.records.dto.PatientEdit;
import dev.aymen.etaye.records.dto.PatientResponse;
import dev.aymen.etaye.records.dto.RecordResponse;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientRecordRepository patientRecordRepository;
    private final AuditService auditService;
    private final OutboxService outboxService;
    private final IdempotencyService idempotencyService;
    private final PracticeTransaction practiceTransaction;
    private final EntityManager entityManager;

    public PatientService(PatientRepository patientRepository,
                          PatientRecordRepository patientRecordRepository,
                          AuditService auditService,
                          OutboxService outboxService,
                          IdempotencyService idempotencyService,
                          PracticeTransaction practiceTransaction,
                          EntityManager entityManager) {
        this.patientRepository = patientRepository;
        this.patientRecordRepository = patientRecordRepository;
        this.auditService = auditService;
        this.outboxService = outboxService;
        this.idempotencyService = idempotencyService;
        this.practiceTransaction = practiceTransaction;
        this.entityManager = entityManager;
    }

    /**
     * Executes the createPatient carrier operation inside a practice-scoped transaction.
     * Atomically creates Patient, PatientRecord, AuditEvent, OutboxEvent, and IdempotencyReceipt.
     */
    public PatientResponse createPatient(UUID practiceId, UUID actorAccountId, String correlationId,
                                         String idempotencyKey, PatientEdit edit) {
        Objects.requireNonNull(practiceId, "practiceId must not be null");
        Objects.requireNonNull(actorAccountId, "actorAccountId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(edit, "edit must not be null");

        return practiceTransaction.execute(practiceId, () -> {
            String keyDigest = idempotencyService.computeSha256(idempotencyKey);
            java.util.Map<String, Object> payload = new java.util.TreeMap<>();
            payload.put("contactEmail", edit.contactEmail());
            payload.put("dateOfBirth", edit.dateOfBirth() != null ? edit.dateOfBirth().toString() : null);
            payload.put("firstName", edit.firstName());
            payload.put("lastName", edit.lastName());
            payload.put("phone", edit.phone());
            String bodyHash = idempotencyService.computeCanonicalBodyHash(payload);

            // Transaction-scoped advisory lock serializes concurrent requests on the same key
            idempotencyService.acquireLock(practiceId, actorAccountId, "createPatient", keyDigest);

            // Check if prior receipt exists
            Optional<UUID> existingIdOpt = idempotencyService.checkReceipt(
                    practiceId, actorAccountId, "createPatient", keyDigest, bodyHash
            );

            if (existingIdOpt.isPresent()) {
                UUID existingPatientId = existingIdOpt.get();
                Patient existingPatient = patientRepository.findByIdAndPracticeId(existingPatientId, practiceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
                PatientRecord existingRecord = patientRecordRepository.findByPatientIdAndPracticeId(existingPatientId, practiceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
                return PatientResponse.of(existingPatient, existingRecord.getId());
            }

            // 1. Create Patient
            Patient patient = new Patient(
                    practiceId,
                    edit.firstName(),
                    edit.lastName(),
                    edit.dateOfBirth(),
                    edit.contactEmail(),
                    edit.phone()
            );
            patient = patientRepository.saveAndFlush(patient);
            entityManager.refresh(patient);

            // 2. Create PatientRecord
            PatientRecord record = new PatientRecord(practiceId, patient.getId());
            record = patientRecordRepository.saveAndFlush(record);

            // 3. Record Audit Event
            auditService.recordEvent(
                    practiceId,
                    actorAccountId,
                    "PATIENT_CREATED",
                    "Patient",
                    patient.getId(),
                    patient.getVersion(),
                    "SUCCESS",
                    correlationId,
                    "{\"recordId\":\"" + record.getId() + "\"}"
            );

            // 4. Record Outbox Event
            outboxService.publish(
                    practiceId,
                    "PATIENT_CREATED",
                    patient.getId(),
                    patient.getVersion(),
                    "patient-created-" + patient.getId(),
                    "{\"patientId\":\"" + patient.getId() + "\",\"recordId\":\"" + record.getId() + "\"}"
            );

            // 5. Record Idempotency Receipt
            idempotencyService.recordReceipt(
                    practiceId,
                    actorAccountId,
                    "createPatient",
                    keyDigest,
                    bodyHash,
                    patient.getId()
            );

            return PatientResponse.of(patient, record.getId());
        });
    }

    /**
     * Executes the getPatientRecord carrier operation inside a practice-scoped transaction.
     */
    public RecordResponse getPatientRecord(UUID practiceId, UUID recordId) {
        Objects.requireNonNull(practiceId, "practiceId must not be null");
        Objects.requireNonNull(recordId, "recordId must not be null");

        return practiceTransaction.execute(practiceId, () -> {
            PatientRecord record = patientRecordRepository.findByIdAndPracticeId(recordId, practiceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

            Patient patient = patientRepository.findByIdAndPracticeId(record.getPatientId(), practiceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

            return RecordResponse.of(record, patient);
        });
    }
}
