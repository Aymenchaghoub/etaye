package dev.aymen.etaye.records.web;

import dev.aymen.etaye.identity.dto.AuthenticatedAccount;
import dev.aymen.etaye.infrastructure.error.InvalidIdempotencyKeyException;
import dev.aymen.etaye.infrastructure.security.CorrelationIdFilter;
import dev.aymen.etaye.infrastructure.security.PracticeSecurityService;
import dev.aymen.etaye.records.dto.PatientRegistration;
import dev.aymen.etaye.records.dto.SchedulingPatientPageResponse;
import dev.aymen.etaye.records.dto.SchedulingPatientResponse;
import dev.aymen.etaye.records.service.PatientSearchService;
import dev.aymen.etaye.records.service.PatientService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Scheduling lookup and demographic-only registration (PD-001, UD-17). It exposes the minimal
 * SchedulingPatient projection and never a clinical route; registration needs two explicit grants.
 */
@RestController
public class SchedulingPatientController {

    private final PatientService patientService;
    private final PatientSearchService patientSearchService;
    private final PracticeSecurityService practiceSecurityService;

    public SchedulingPatientController(PatientService patientService,
                                       PatientSearchService patientSearchService,
                                       PracticeSecurityService practiceSecurityService) {
        this.patientService = patientService;
        this.patientSearchService = patientSearchService;
        this.practiceSecurityService = practiceSecurityService;
    }

    @GetMapping(value = "/api/v1/practices/{practiceId}/scheduling/patients", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SchedulingPatientPageResponse> searchSchedulingPatients(
            @PathVariable UUID practiceId,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletRequest request) {

        AuthenticatedAccount account = practiceSecurityService.requireCapability(request, practiceId, "SCHEDULE_MANAGE");
        SchedulingPatientPageResponse page = patientSearchService.searchScheduling(practiceId, account.account().id(), q, cursor, limit);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(page);
    }

    @PostMapping(value = "/api/v1/practices/{practiceId}/scheduling/patients", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SchedulingPatientResponse> registerSchedulingPatient(
            @PathVariable UUID practiceId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PatientRegistration registration,
            HttpServletRequest request) {

        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key header is required and must be between 16 and 128 characters");
        }
        practiceSecurityService.requireCapability(request, practiceId, "SCHEDULE_MANAGE");
        AuthenticatedAccount account = practiceSecurityService.requireCapability(request, practiceId, "PATIENT_REGISTER");

        SchedulingPatientResponse response = patientService.registerSchedulingPatient(practiceId, account.account().id(),
                CorrelationIdFilter.getCorrelationId(request), idempotencyKey, registration);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
