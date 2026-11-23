package dev.aymen.etaye.records.web;

import dev.aymen.etaye.identity.dto.AuthenticatedAccount;
import dev.aymen.etaye.infrastructure.error.InvalidIdempotencyKeyException;
import dev.aymen.etaye.infrastructure.error.InvalidParameterException;
import dev.aymen.etaye.infrastructure.error.PreconditionRequiredException;
import dev.aymen.etaye.infrastructure.security.CorrelationIdFilter;
import dev.aymen.etaye.infrastructure.security.PracticeSecurityService;
import dev.aymen.etaye.records.dto.PatientEdit;
import dev.aymen.etaye.records.dto.PatientPageResponse;
import dev.aymen.etaye.records.dto.PatientRegistration;
import dev.aymen.etaye.records.dto.PatientResponse;
import dev.aymen.etaye.records.dto.RecordResponse;
import dev.aymen.etaye.records.service.PatientSearchService;
import dev.aymen.etaye.records.service.PatientService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class PatientController {

    private final PatientService patientService;
    private final PatientSearchService patientSearchService;
    private final PracticeSecurityService practiceSecurityService;

    public PatientController(PatientService patientService,
                             PatientSearchService patientSearchService,
                             PracticeSecurityService practiceSecurityService) {
        this.patientService = patientService;
        this.patientSearchService = patientSearchService;
        this.practiceSecurityService = practiceSecurityService;
    }

    @GetMapping(value = "/api/v1/practices/{practiceId}/patients", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PatientPageResponse> searchPatients(
            @PathVariable UUID practiceId,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletRequest request) {

        AuthenticatedAccount account = practiceSecurityService.requireCapability(request, practiceId, "CLINICAL_READ");
        PatientPageResponse page = patientSearchService.searchClinical(practiceId, account.account().id(), q, cursor, limit);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(page);
    }

    @PatchMapping(value = "/api/v1/practices/{practiceId}/patients/{patientId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PatientResponse> editPatient(
            @PathVariable UUID practiceId,
            @PathVariable UUID patientId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody PatientEdit edit,
            HttpServletRequest request) {

        AuthenticatedAccount account = practiceSecurityService.requireCapability(request, practiceId, "CLINICAL_WRITE");
        long expectedVersion = parseIfMatch(ifMatch);
        PatientResponse response = patientService.editPatient(practiceId, account.account().id(),
                CorrelationIdFilter.getCorrelationId(request), patientId, expectedVersion, edit);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"" + response.version() + "\"")
                .body(response);
    }

    static long parseIfMatch(String header) {
        if (header == null || header.isBlank()) {
            throw new PreconditionRequiredException("If-Match header is required");
        }
        String value = header.strip();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            long version = Long.parseLong(value);
            if (version < 0) {
                throw new InvalidParameterException("If-Match must hold a version");
            }
            return version;
        } catch (NumberFormatException malformed) {
            throw new InvalidParameterException("If-Match must hold a version");
        }
    }

    @PostMapping(value = "/api/v1/practices/{practiceId}/patients", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PatientResponse> createPatient(
            @PathVariable UUID practiceId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PatientRegistration edit,
            HttpServletRequest request) {

        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key header is required and must be between 16 and 128 characters");
        }

        AuthenticatedAccount account = practiceSecurityService.requireCapability(request, practiceId, "CLINICAL_WRITE");
        String correlationId = CorrelationIdFilter.getCorrelationId(request);

        PatientResponse response = patientService.createPatient(
                practiceId,
                account.account().id(),
                correlationId,
                idempotencyKey,
                edit
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.ETAG, "\"" + response.version() + "\"")
                .body(response);
    }

    @GetMapping(value = "/api/v1/practices/{practiceId}/records/{recordId}", produces = MediaType.APPLICATION_JSON_VALUE)
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
