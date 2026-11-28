package dev.aymen.etaye.records.web;

import dev.aymen.etaye.identity.dto.AccessContext;
import dev.aymen.etaye.infrastructure.error.InvalidParameterException;
import dev.aymen.etaye.infrastructure.security.PracticeSecurityService;
import dev.aymen.etaye.records.domain.LabResultRepository.Filters;
import dev.aymen.etaye.records.service.LabResultService;
import dev.aymen.etaye.records.service.LabResultService.AnalytePage;
import dev.aymen.etaye.records.service.LabResultService.Context;
import dev.aymen.etaye.records.service.LabResultService.LabPage;
import dev.aymen.etaye.records.service.LabResultService.PageRequest;
import dev.aymen.etaye.records.service.LabResultService.RevisionList;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static dev.aymen.etaye.records.web.RecordEvidenceController.instant;

@RestController
@RequestMapping("/api/v1/practices/{practiceId}/records/{recordId}/lab-results")
public class LabResultController {

    private final LabResultService service;
    private final PracticeSecurityService security;

    public LabResultController(LabResultService service, PracticeSecurityService security) {
        this.service = service;
        this.security = security;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LabPage> list(@PathVariable UUID practiceId, @PathVariable UUID recordId,
                                       @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit,
                                       @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                       @RequestParam(required = false) String asOf, HttpServletRequest request) {
        Context context = context(request, practiceId, recordId);
        String[] selectors = request.getParameterValues("analyteKey");
        if (selectors != null && selectors.length != 1) {
            throw new InvalidParameterException("only one analyte selection is allowed");
        }
        return noStore(service.list(context, new PageRequest(cursor, limit, instant("asOf", asOf)),
                new Filters(selectors == null ? null : selectors[0], instant("from", from), instant("to", to))));
    }

    @GetMapping(value = "/analytes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnalytePage> analytes(@PathVariable UUID practiceId, @PathVariable UUID recordId,
                                                @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit,
                                                @RequestParam(required = false) String asOf, HttpServletRequest request) {
        Context context = context(request, practiceId, recordId);
        return noStore(service.analytes(context, new PageRequest(cursor, limit, instant("asOf", asOf))));
    }

    @GetMapping(value = "/{labResultId}/revisions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RevisionList> revisions(@PathVariable UUID practiceId, @PathVariable UUID recordId,
                                                   @PathVariable UUID labResultId,
                                                   @RequestParam(required = false) String asOf, HttpServletRequest request) {
        Context context = context(request, practiceId, recordId);
        return noStore(service.revisions(context, labResultId, instant("asOf", asOf)));
    }

    private Context context(HttpServletRequest request, UUID practiceId, UUID recordId) {
        var account = security.requireCapability(request, practiceId, "CLINICAL_READ");
        String timezone = account.contexts().stream()
                .filter(grant -> grant.kind() == AccessContext.Kind.MEMBERSHIP && grant.practiceId().equals(practiceId))
                .map(AccessContext::timezone)
                .findFirst().orElseThrow(() -> new IllegalStateException("Current membership has no practice time zone"));
        return new Context(practiceId, recordId, account.account().id(), timezone);
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
