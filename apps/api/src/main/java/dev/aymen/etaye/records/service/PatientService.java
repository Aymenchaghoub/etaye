package dev.aymen.etaye.records.service;

import dev.aymen.etaye.audit.service.AuditService;
import dev.aymen.etaye.communications.service.OutboxService;
import dev.aymen.etaye.infrastructure.error.ConflictWithCandidatesException;
import dev.aymen.etaye.infrastructure.error.InvalidParameterException;
import dev.aymen.etaye.infrastructure.error.ResourceNotFoundException;
import dev.aymen.etaye.infrastructure.error.StaleVersionException;
import dev.aymen.etaye.infrastructure.idempotency.IdempotencyService;
import dev.aymen.etaye.infrastructure.transaction.PracticeTransaction;
import dev.aymen.etaye.records.domain.Patient;
import dev.aymen.etaye.records.domain.PatientRecord;
import dev.aymen.etaye.records.domain.PatientRecordRepository;
import dev.aymen.etaye.records.domain.PatientRepository;
import dev.aymen.etaye.records.domain.PatientDirectoryRepository;
import dev.aymen.etaye.records.domain.PatientView;
import dev.aymen.etaye.records.dto.PatientEdit;
import dev.aymen.etaye.records.dto.PatientRegistration;
import dev.aymen.etaye.records.dto.PatientResponse;
import dev.aymen.etaye.records.dto.RecordResponse;
import dev.aymen.etaye.records.dto.SchedulingPatientResponse;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PatientService {

    private static final int MAX_CANDIDATES = 10;

    private final PatientRepository patientRepository;
    private final PatientRecordRepository patientRecordRepository;
    private final PatientDirectoryRepository directory;
    private final AuditService auditService;
    private final OutboxService outboxService;
    private final IdempotencyService idempotencyService;
    private final PracticeTransaction practiceTransaction;
    private final EntityManager entityManager;

    public PatientService(PatientRepository patientRepository,
                          PatientRecordRepository patientRecordRepository,
                          PatientDirectoryRepository directory,
                          AuditService auditService,
                          OutboxService outboxService,
                          IdempotencyService idempotencyService,
                          PracticeTransaction practiceTransaction,
                          EntityManager entityManager) {
        this.patientRepository = patientRepository;
        this.patientRecordRepository = patientRecordRepository;
        this.directory = directory;
        this.auditService = auditService;
        this.outboxService = outboxService;
        this.idempotencyService = idempotencyService;
        this.practiceTransaction = practiceTransaction;
        this.entityManager = entityManager;
    }

    /**
     * Executes createPatient inside a practice-scoped transaction. Atomically creates Patient,
     * PatientRecord, AuditEvent, OutboxEvent and IdempotencyReceipt, after the likely-duplicate check.
     */
    public PatientResponse createPatient(UUID practiceId, UUID actorAccountId, String correlationId,
                                         String idempotencyKey, PatientRegistration registration) {
        return PatientResponse.of(register("createPatient", "CLINICAL", practiceId, actorAccountId, correlationId,
                idempotencyKey, registration));
    }

    /** The scheduling registration: same atomic creation, minimal projection, no clinical access. */
    public SchedulingPatientResponse registerSchedulingPatient(UUID practiceId, UUID actorAccountId, String correlationId,
                                                              String idempotencyKey, PatientRegistration registration) {
        return SchedulingPatientResponse.of(register("registerSchedulingPatient", "SCHEDULING", practiceId, actorAccountId,
                correlationId, idempotencyKey, registration));
    }

    /** Names are stored in canonical composition so that equal letters are equal bytes. */
    private static String nfc(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC);
    }

    private PatientView register(String operation, String channel, UUID practiceId, UUID actorAccountId, String correlationId,
                                 String idempotencyKey, PatientRegistration registration) {
        Objects.requireNonNull(practiceId, "practiceId must not be null");
        Objects.requireNonNull(actorAccountId, "actorAccountId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(registration, "registration must not be null");
        if (new HashSet<>(registration.confirmations()).size() != registration.confirmations().size()) {
            throw new InvalidParameterException("confirmedDistinctRecordIds must not repeat a record");
        }

        return practiceTransaction.execute(practiceId, () -> {
            String keyDigest = idempotencyService.computeSha256(idempotencyKey);
            Map<String, Object> payload = new TreeMap<>();
            payload.put("contactEmail", registration.contactEmail());
            payload.put("dateOfBirth", registration.dateOfBirth() != null ? registration.dateOfBirth().toString() : null);
            payload.put("firstName", registration.firstName());
            payload.put("lastName", registration.lastName());
            payload.put("phone", registration.phone());
            if (!registration.confirmations().isEmpty()) {
                // Only present when a human confirmed, so receipts of plain registrations keep their hash.
                payload.put("confirmedDistinctRecordIds", registration.confirmations().stream()
                        .map(UUID::toString).sorted().collect(Collectors.joining(",")));
            }
            String bodyHash = idempotencyService.computeCanonicalBodyHash(payload);

            // Transaction-scoped advisory lock serializes concurrent requests on the same key
            idempotencyService.acquireLock(practiceId, actorAccountId, operation, keyDigest);

            Optional<UUID> existingIdOpt = idempotencyService.checkReceipt(practiceId, actorAccountId, operation, keyDigest, bodyHash);
            if (existingIdOpt.isPresent()) {
                return directory.findView(practiceId, existingIdOpt.get())
                        .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
            }

            String firstName = nfc(registration.firstName());
            String lastName = nfc(registration.lastName());
            String firstKey = directory.normalizeName(firstName);
            String lastKey = directory.normalizeName(lastName);
            // Two registrations of the same likely identity cannot both pass the check: the second waits for the first.
            directory.lockIdentity(practiceId, firstKey, lastKey, registration.dateOfBirth());
            List<PatientView> found = directory.findDuplicateCandidates(practiceId, firstKey, lastKey,
                    registration.dateOfBirth(), MAX_CANDIDATES + 1);
            if (!found.isEmpty()) {
                List<PatientView> candidates = found.stream().limit(MAX_CANDIDATES).toList();
                Set<UUID> confirmed = new HashSet<>(registration.confirmations());
                // More than ten matches can never all be named, so the registration stays refused.
                boolean allConfirmed = found.size() <= MAX_CANDIDATES
                        && candidates.stream().allMatch(candidate -> confirmed.contains(candidate.recordId()));
                if (!allConfirmed) {
                    throw new ConflictWithCandidatesException("DUPLICATE_PATIENT",
                            "A similar patient already exists in this practice",
                            candidates.stream().map(SchedulingPatientResponse::of).toList());
                }
            }

            Patient patient = new Patient(practiceId, firstName, lastName,
                    registration.dateOfBirth(), registration.contactEmail(), registration.phone());
            patient = patientRepository.saveAndFlush(patient);
            entityManager.refresh(patient);

            PatientRecord record = patientRecordRepository.saveAndFlush(new PatientRecord(practiceId, patient.getId()));

            auditService.recordEvent(practiceId, actorAccountId, "PATIENT_CREATED", "Patient", patient.getId(),
                    patient.getVersion(), "SUCCESS", correlationId,
                    "{\"recordId\":\"" + record.getId() + "\",\"channel\":\"" + channel + "\",\"confirmedDistinct\":"
                            + registration.confirmations().size() + "}");

            outboxService.publish(practiceId, "PATIENT_CREATED", patient.getId(), patient.getVersion(),
                    "patient-created-" + patient.getId(),
                    "{\"patientId\":\"" + patient.getId() + "\",\"recordId\":\"" + record.getId() + "\"}");

            idempotencyService.recordReceipt(practiceId, actorAccountId, operation, keyDigest, bodyHash, patient.getId());

            UUID createdId = patient.getId();
            return directory.findView(practiceId, createdId).orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
        });
    }

    /**
     * Corrects an identity under the If-Match version. The patient and record identifiers never
     * change; the correction and its audit and outbox rows commit together or not at all.
     */
    public PatientResponse editPatient(UUID practiceId, UUID actorAccountId, String correlationId, UUID patientId,
                                       long expectedVersion, PatientEdit edit) {
        Objects.requireNonNull(practiceId, "practiceId must not be null");
        Objects.requireNonNull(actorAccountId, "actorAccountId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(patientId, "patientId must not be null");
        Objects.requireNonNull(edit, "edit must not be null");

        return practiceTransaction.execute(practiceId, () -> {
            long current = directory.lockVersion(practiceId, patientId)
                    .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
            if (current != expectedVersion
                    || !directory.updateIdentity(practiceId, patientId, expectedVersion, nfc(edit.firstName()), nfc(edit.lastName()),
                    edit.dateOfBirth(), edit.contactEmail(), edit.phone())) {
                throw new StaleVersionException("The patient was changed by another request");
            }
            PatientView view = directory.findView(practiceId, patientId)
                    .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
            directory.touchRecord(practiceId, view.recordId());
            auditService.recordEvent(practiceId, actorAccountId, "PATIENT_UPDATED", "Patient", patientId, view.version(),
                    "SUCCESS", correlationId, "{\"recordId\":\"" + view.recordId() + "\"}");
            outboxService.publish(practiceId, "PATIENT_UPDATED", patientId, view.version(),
                    "patient-updated-" + patientId + "-" + view.version(),
                    "{\"patientId\":\"" + patientId + "\",\"recordId\":\"" + view.recordId() + "\"}");
            return PatientResponse.of(view);
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
