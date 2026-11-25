package dev.aymen.etaye.records.web;

import dev.aymen.etaye.identity.dto.AuthenticatedAccount;
import dev.aymen.etaye.infrastructure.error.InvalidParameterException;
import dev.aymen.etaye.infrastructure.security.PracticeSecurityService;
import dev.aymen.etaye.records.domain.AssertionCategory;
import dev.aymen.etaye.records.domain.SourceKind;
import dev.aymen.etaye.records.dto.AssertionPageResponse;
import dev.aymen.etaye.records.dto.HistorySummaryResponse;
import dev.aymen.etaye.records.dto.SourcePageResponse;
import dev.aymen.etaye.records.dto.SourceResponse;
import dev.aymen.etaye.records.dto.TimelinePageResponse;
import dev.aymen.etaye.records.service.RecordEvidenceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * The record evidence reads of story 3.2. The capability is checked against the actor's current membership in the
 * route practice before anything is read; every response is private and no-store, and none carries an ETag.
 */
@RestController
public class RecordEvidenceController {

    private static final String CAPABILITY = "CLINICAL_READ";

    private final RecordEvidenceService service;
    private final PracticeSecurityService practiceSecurityService;

    public RecordEvidenceController(RecordEvidenceService service, PracticeSecurityService practiceSecurityService) {
        this.service = service;
        this.practiceSecurityService = practiceSecurityService;
    }

    @GetMapping(value = "/api/v1/practices/{practiceId}/records/{recordId}/timeline", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TimelinePageResponse> getTimeline(
            @PathVariable UUID practiceId,
            @PathVariable UUID recordId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "kind", required = false) List<String> kind,
            @RequestParam(value = "asOf", required = false) String asOf,
            HttpServletRequest request) {
        AuthenticatedAccount account = practiceSecurityService.requireCapability(request, practiceId, CAPABILITY);
        TimelinePageResponse page = service.timeline(practiceId, account.account().id(), recordId, cursor, limit,
                instant("from", from), instant("to", to), kinds(kind), instant("asOf", asOf));
        return noStore(page);
    }

    @GetMapping(value = "/api/v1/practices/{practiceId}/records/{recordId}/sources", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SourcePageResponse> listRecordSources(
            @PathVariable UUID practiceId,
            @PathVariable UUID recordId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "asOf", required = false) String asOf,
            HttpServletRequest request) {
        AuthenticatedAccount account = practiceSecurityService.requireCapability(request, practiceId, CAPABILITY);
        return noStore(service.sources(practiceId, account.account().id(), recordId, cursor, limit, instant("asOf", asOf)));
    }

    @GetMapping(value = "/api/v1/practices/{practiceId}/sources/{sourceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SourceResponse> getSourceRevision(
            @PathVariable UUID practiceId,
            @PathVariable UUID sourceId,
            @RequestParam(value = "asOf", required = false) String asOf,
            HttpServletRequest request) {
        practiceSecurityService.requireCapability(request, practiceId, CAPABILITY);
        return noStore(service.source(practiceId, sourceId, instant("asOf", asOf)));
    }

    @GetMapping(value = "/api/v1/practices/{practiceId}/records/{recordId}/assertions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AssertionPageResponse> listAssertions(
            @PathVariable UUID practiceId,
            @PathVariable UUID recordId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "asOf", required = false) String asOf,
            HttpServletRequest request) {
        AuthenticatedAccount account = practiceSecurityService.requireCapability(request, practiceId, CAPABILITY);
        return noStore(service.assertions(practiceId, account.account().id(), recordId, cursor, limit,
                category == null ? null : parse(AssertionCategory.class, category), instant("asOf", asOf)));
    }

    @GetMapping(value = "/api/v1/practices/{practiceId}/records/{recordId}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HistorySummaryResponse> getHistorySummary(
            @PathVariable UUID practiceId,
            @PathVariable UUID recordId,
            @RequestParam(value = "asOf", required = false) String asOf,
            HttpServletRequest request) {
        practiceSecurityService.requireCapability(request, practiceId, CAPABILITY);
        return noStore(service.history(practiceId, recordId, instant("asOf", asOf)));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    /** An absolute ISO 8601 date-time. A client sends UTC or an offset; a local time without offset is refused. */
    static Instant instant(String name, String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException malformed) {
            throw new InvalidParameterException(name + " must be an ISO 8601 date-time with an offset");
        }
    }

    private static List<SourceKind> kinds(List<String> values) {
        return values == null ? List.of() : values.stream().map(value -> parse(SourceKind.class, value)).toList();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException unknown) {
            throw new InvalidParameterException("unknown value for " + type.getSimpleName());
        }
    }
}
