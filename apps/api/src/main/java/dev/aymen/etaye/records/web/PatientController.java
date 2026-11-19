package dev.aymen.etaye.records.web;

import dev.aymen.etaye.identity.domain.AuthenticatedAccount;
import dev.aymen.etaye.infrastructure.error.InvalidIdempotencyKeyException;
import dev.aymen.etaye.infrastructure.security.CorrelationIdFilter;
import dev.aymen.etaye.infrastructure.security.PracticeSecurityService;
import dev.aymen.etaye.records.service.PatientService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class PatientController {

    private final PatientService patientService;
    private final PracticeSecurityService practiceSecurityService;

    public PatientController(PatientService patientService,
                             PracticeSecurityService practiceSecurityService) {
        this.patientService = patientService;
        this.practiceSecurityService = practiceSecurityService;
    }

    @PostMapping("/api/v1/practices/{practiceId}/patients")
    public ResponseEntity<PatientResponse> createPatient(
            @PathVariable UUID practiceId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Test-Fail-After-Write", required = false) String testFailAfterWrite,
            @Valid @RequestBody PatientEdit edit,
            HttpServletRequest request) {

        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key header is required and must be between 16 and 128 characters");
        }

        AuthenticatedAccount account = practiceSecurityService.requireCapability(request, practiceId, "CLINICAL_WRITE");
        String correlationId = CorrelationIdFilter.getCorrelationId(request);

        Runnable failureHook = "true".equalsIgnoreCase(testFailAfterWrite)
                ? () -> { throw new RuntimeException("Simulated failure after domain write"); }
                : null;

        PatientResponse response = patientService.createPatient(
                practiceId,
                account.account().id(),
                correlationId,
                idempotencyKey,
                edit,
                failureHook
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.ETAG, "\"" + response.version() + "\"")
                .body(response);
    }

    @GetMapping("/api/v1/practices/{practiceId}/records/{recordId}")
    public ResponseEntity<RecordResponse> getPatientRecord(
            @PathVariable UUID practiceId,
            @PathVariable UUID recordId,
            HttpServletRequest request) {

        practiceSecurityService.requireCapability(request, practiceId, "CLINICAL_READ");

        RecordResponse response = patientService.getPatientRecord(practiceId, recordId);

        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"" + response.version() + "\"")
                .body(response);
    }
}
