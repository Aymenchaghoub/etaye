package dev.aymen.etaye.records.service;

import dev.aymen.etaye.infrastructure.error.InvalidParameterException;
import dev.aymen.etaye.infrastructure.error.ResourceNotFoundException;
import dev.aymen.etaye.infrastructure.transaction.PracticeTransaction;
import dev.aymen.etaye.records.domain.AssertionCategory;
import dev.aymen.etaye.records.domain.AssertionKind;
import dev.aymen.etaye.records.domain.AssertionView;
import dev.aymen.etaye.records.domain.EvidenceRepository;
import dev.aymen.etaye.records.domain.EvidenceRepository.Position;
import dev.aymen.etaye.records.domain.MeasurementRepository;
import dev.aymen.etaye.records.domain.MeasurementView;
import dev.aymen.etaye.records.domain.SectionState;
import dev.aymen.etaye.records.domain.SectionStateRule;
import dev.aymen.etaye.records.domain.SourceKind;
import dev.aymen.etaye.records.domain.SourceView;
import dev.aymen.etaye.records.dto.AssertionPageResponse;
import dev.aymen.etaye.records.dto.AssertionResponse;
import dev.aymen.etaye.records.dto.HistorySectionResponse;
import dev.aymen.etaye.records.dto.HistorySummaryResponse;
import dev.aymen.etaye.records.dto.MeasurementPageResponse;
import dev.aymen.etaye.records.dto.MeasurementResponse;
import dev.aymen.etaye.records.dto.MeasurementRevisionListResponse;
import dev.aymen.etaye.records.dto.SourcePageResponse;
import dev.aymen.etaye.records.dto.SourceResponse;
import dev.aymen.etaye.records.dto.TimelineItemResponse;
import dev.aymen.etaye.records.dto.TimelinePageResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Read side of the record evidence (story 3.2): the chronology, the sources, the statements and the derived state of
 * the three history sections, for the current record or as known at a past instant (UD-32). Everything runs in a
 * practice transaction after the controller checked the capability; the record is then resolved inside that practice,
 * so a foreign or absent record is the same not-found. No routine read is audited (D-62) and nothing is written.
 */
@Service
public class RecordEvidenceService {

    public static final int DEFAULT_LIMIT = 25;
    public static final int MAX_LIMIT = 100;
    public static final int MAX_CURSOR_LENGTH = 1024;
    public static final int MAX_KINDS = 5;
    /** The statements listed per section; the state is always derived from all of them. */
    public static final int MAX_SECTION_STATEMENTS = 100;

    public static final int MAX_CONCEPTS = 10;
    public static final int MAX_CONCEPT_LENGTH = 100;

    private final EvidenceRepository evidence;
    private final MeasurementRepository measurements;
    private final PracticeTransaction practiceTransaction;
    private final Clock clock;

    public RecordEvidenceService(EvidenceRepository evidence, MeasurementRepository measurements,
                                 PracticeTransaction practiceTransaction, ObjectProvider<Clock> clock) {
        this.evidence = evidence;
        this.measurements = measurements;
        this.practiceTransaction = practiceTransaction;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    private record Paging(int limit, String cursor) {
    }

    private Paging paging(Integer requestedLimit, String cursor) {
        int asked = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        if (asked < 1 || asked > MAX_LIMIT) {
            throw new InvalidParameterException("limit out of range");
        }
        if (cursor != null && cursor.length() > MAX_CURSOR_LENGTH) {
            throw new InvalidParameterException("cursor too long");
        }
        return new Paging(Math.min(asked, MAX_LIMIT), cursor);
    }

    /**
     * The instant of the view: the requested past instant, or now. A future instant is refused, never clamped; the request
     * is compared as the client wrote it, then cut down to the microsecond PostgreSQL stores, because rounding it up would
     * let a revision recorded just after the requested instant into the view.
     */
    private Instant viewInstant(Instant asOf) {
        Instant now = clock.instant();
        if (asOf != null && asOf.isAfter(now)) {
            throw new InvalidParameterException("asOf must not be in the future");
        }
        return (asOf == null ? now : asOf).truncatedTo(ChronoUnit.MICROS);
    }

    /** The inclusive lower bound of an event date: a fractional microsecond rounds up so no earlier event passes. */
    private static Instant lowerBound(Instant from) {
        return from == null ? null : ceilMicros(from);
    }

    /** The exclusive upper bound: rounded up in the same way, so an event before the written bound is never excluded. */
    private static Instant upperBound(Instant to) {
        return to == null ? null : ceilMicros(to);
    }

    private static Instant ceilMicros(Instant instant) {
        Instant floor = instant.truncatedTo(ChronoUnit.MICROS);
        return floor.equals(instant) ? floor : floor.plus(1, ChronoUnit.MICROS);
    }

    private void requireRecord(UUID practiceId, UUID recordId) {
        if (!evidence.recordExists(practiceId, recordId)) {
            throw new ResourceNotFoundException("Resource not found");
        }
    }

    public TimelinePageResponse timeline(UUID practiceId, UUID actorId, UUID recordId, String cursor, Integer limit,
                                         Instant from, Instant to, List<SourceKind> kinds, Instant asOf) {
        Paging page = paging(limit, cursor);
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidParameterException("from must not be after to");
        }
        List<SourceKind> kindFilter = kinds == null ? List.of() : kinds.stream().distinct().sorted().toList();
        if (kindFilter.size() > MAX_KINDS) {
            throw new InvalidParameterException("too many kinds");
        }
        return practiceTransaction.execute(practiceId, () -> {
            requireRecord(practiceId, recordId);
            Instant view = viewInstant(asOf);
            String binding = binding("timeline", practiceId, actorId, recordId, asOf,
                    kindFilter + "|" + from + "|" + to);
            Position after = sourceCursor(practiceId, recordId, view, page.cursor(), binding);
            List<SourceView> rows = evidence.pageSources(practiceId, recordId, view, kindFilter, lowerBound(from), upperBound(to), after,
                    page.limit() + 1);
            return pageOf(rows, page.limit(), binding, SourceView::id, TimelineItemResponse::of,
                    (items, next) -> new TimelinePageResponse(items, next));
        });
    }

    public SourcePageResponse sources(UUID practiceId, UUID actorId, UUID recordId, String cursor, Integer limit,
                                      Instant asOf) {
        Paging page = paging(limit, cursor);
        return practiceTransaction.execute(practiceId, () -> {
            requireRecord(practiceId, recordId);
            Instant view = viewInstant(asOf);
            String binding = binding("sources", practiceId, actorId, recordId, asOf, "");
            Position after = sourceCursor(practiceId, recordId, view, page.cursor(), binding);
            List<SourceView> rows = evidence.pageSources(practiceId, recordId, view, List.of(), null, null, after,
                    page.limit() + 1);
            return pageOf(rows, page.limit(), binding, SourceView::id, SourceResponse::of,
                    (items, next) -> new SourcePageResponse(items, next));
        });
    }

    public SourceResponse source(UUID practiceId, UUID sourceId, Instant asOf) {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Instant view = asOf == null ? null : viewInstant(asOf);
        return practiceTransaction.execute(practiceId, () -> evidence.findSource(practiceId, sourceId, view)
                .map(SourceResponse::of)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found")));
    }

    public AssertionPageResponse assertions(UUID practiceId, UUID actorId, UUID recordId, String cursor, Integer limit,
                                            AssertionCategory category, Instant asOf) {
        Paging page = paging(limit, cursor);
        return practiceTransaction.execute(practiceId, () -> {
            requireRecord(practiceId, recordId);
            Instant view = viewInstant(asOf);
            String binding = binding("assertions", practiceId, actorId, recordId, asOf, String.valueOf(category));
            UUID cursorId = decode(page.cursor(), binding);
            Position after = cursorId == null ? null : evidence.assertionPosition(practiceId, recordId, view, cursorId)
                    .orElseThrow(() -> new InvalidParameterException("cursor does not belong to this view"));
            List<AssertionView> rows = evidence.pageAssertions(practiceId, recordId, view, category, after,
                    page.limit() + 1);
            return pageOf(rows, page.limit(), binding, AssertionView::id, AssertionResponse::of,
                    (items, next) -> new AssertionPageResponse(items, next));
        });
    }

    /**
     * The measurements of the view (story 3.3): the current revision of every lineage, newest observation first. The
     * concept filter compares exact stored codes, so two spellings of a concept or a unit are never merged here.
     */
    public MeasurementPageResponse measurements(UUID practiceId, UUID actorId, UUID recordId, String cursor, Integer limit,
                                                Instant from, Instant to, List<String> concepts, Instant asOf) {
        Paging page = paging(limit, cursor);
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidParameterException("from must not be after to");
        }
        List<String> conceptFilter = concepts == null ? List.of() : concepts.stream().distinct().sorted().toList();
        if (conceptFilter.size() > MAX_CONCEPTS) {
            throw new InvalidParameterException("too many concepts");
        }
        if (conceptFilter.stream().anyMatch(code -> code.isBlank() || code.length() > MAX_CONCEPT_LENGTH)) {
            throw new InvalidParameterException("concept must be 1 to " + MAX_CONCEPT_LENGTH + " characters");
        }
        return practiceTransaction.execute(practiceId, () -> {
            requireRecord(practiceId, recordId);
            Instant view = viewInstant(asOf);
            String binding = binding("measurements", practiceId, actorId, recordId, asOf,
                    conceptFilter + "|" + from + "|" + to);
            UUID cursorId = decode(page.cursor(), binding);
            MeasurementRepository.Position after = cursorId == null ? null
                    : measurements.position(practiceId, recordId, view, cursorId)
                    .orElseThrow(() -> new InvalidParameterException("cursor does not belong to this view"));
            List<MeasurementView> rows = measurements.page(practiceId, recordId, view, conceptFilter, lowerBound(from),
                    upperBound(to), after, page.limit() + 1);
            return pageOf(rows, page.limit(), binding, MeasurementView::id, MeasurementResponse::of,
                    (items, next) -> new MeasurementPageResponse(items, next));
        });
    }

    /** The lineage of one measurement, oldest first, as known at the instant: a later revision is never listed. */
    public MeasurementRevisionListResponse measurementRevisions(UUID practiceId, UUID measurementId, Instant asOf) {
        Objects.requireNonNull(measurementId, "measurementId must not be null");
        Instant view = asOf == null ? null : viewInstant(asOf);
        return practiceTransaction.execute(practiceId, () -> measurements.lineage(practiceId, measurementId, view)
                .map(revisions -> new MeasurementRevisionListResponse(revisions.stream().map(MeasurementResponse::of).toList()))
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found")));
    }

    public HistorySummaryResponse history(UUID practiceId, UUID recordId, Instant asOf) {
        return practiceTransaction.execute(practiceId, () -> {
            requireRecord(practiceId, recordId);
            Instant view = viewInstant(asOf);
            List<AssertionView> all = evidence.allAssertions(practiceId, recordId, view);
            List<HistorySectionResponse> sections = AssertionCategory.DISPLAY_ORDER.stream().map(category -> {
                // The repository order (oldest event first, undated last) is kept; the state is derived from all statements.
                List<AssertionView> statements = all.stream().filter(a -> a.category() == category).toList();
                SectionState state = SectionStateRule.derive(statements.stream()
                        .map(a -> new SectionStateRule.Statement(a.kind(), a.recordedAt(), a.id())).toList());
                List<AssertionResponse> shown = listed(statements).stream().map(AssertionResponse::of).toList();
                return new HistorySectionResponse(category.name(), state.name(), shown, statements.size() > MAX_SECTION_STATEMENTS);
            }).toList();
            return new HistorySummaryResponse(asOf, sections);
        });
    }

    /**
     * The statements a section lists: all of them up to the bound. Beyond it, the latest statement of every kind is kept
     * first, so that the two sides of a conflict can always be read, then the most recent others; the repository order
     * (oldest event first) is restored. The state is derived from all statements either way.
     */
    static List<AssertionView> listed(List<AssertionView> statements) {
        if (statements.size() <= MAX_SECTION_STATEMENTS) {
            return statements;
        }
        java.util.Set<UUID> keep = new java.util.HashSet<>();
        for (AssertionKind kind : AssertionKind.values()) {
            statements.stream().filter(a -> a.kind() == kind)
                    .max(java.util.Comparator.comparing(AssertionView::recordedAt).thenComparing(AssertionView::id))
                    .ifPresent(a -> keep.add(a.id()));
        }
        statements.stream()
                .sorted(java.util.Comparator.comparing(AssertionView::recordedAt).thenComparing(AssertionView::id).reversed())
                .takeWhile(a -> keep.size() < MAX_SECTION_STATEMENTS || keep.contains(a.id()))
                .forEach(a -> keep.add(a.id()));
        return statements.stream().filter(a -> keep.contains(a.id())).toList();
    }

    private Position sourceCursor(UUID practiceId, UUID recordId, Instant view, String cursor, String binding) {
        UUID cursorId = decode(cursor, binding);
        if (cursorId == null) {
            return null;
        }
        // A cursor is resolved in this practice, record and view even when it cannot match, so a foreign or stale one is a 400.
        return evidence.sourcePosition(practiceId, recordId, view, cursorId)
                .orElseThrow(() -> new InvalidParameterException("cursor does not belong to this view"));
    }

    private static <R, T, P> P pageOf(List<R> rows, int limit, String binding, Function<R, UUID> idOf,
                                      Function<R, T> map, java.util.function.BiFunction<List<T>, String, P> wrap) {
        if (rows.size() <= limit) {
            return wrap.apply(rows.stream().map(map).toList(), null);
        }
        List<R> items = rows.subList(0, limit);
        return wrap.apply(items.stream().map(map).toList(), encode(binding, idOf.apply(items.getLast())));
    }

    /** Binds a cursor to the operation, practice, actor, record, view instant and filters. The instant is part of the binding. */
    private static String binding(String operation, UUID practiceId, UUID actorId, UUID recordId, Instant asOf,
                                  String filters) {
        return sha256("v1|" + operation + "|" + practiceId + "|" + actorId + "|" + recordId + "|"
                + (asOf == null ? "now" : asOf.toString()) + "|" + filters);
    }

    private static String encode(String binding, UUID lastId) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((binding + "\n" + lastId).getBytes(StandardCharsets.UTF_8));
    }

    private static UUID decode(String cursor, String binding) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\n", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(parts[0].getBytes(StandardCharsets.UTF_8),
                    binding.getBytes(StandardCharsets.UTF_8))) {
                throw new InvalidParameterException("cursor does not belong to this view");
            }
            return UUID.fromString(parts[1]);
        } catch (IllegalArgumentException malformed) {
            throw new InvalidParameterException("malformed cursor");
        }
    }

    private static String sha256(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
