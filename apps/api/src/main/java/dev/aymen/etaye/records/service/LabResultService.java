package dev.aymen.etaye.records.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.aymen.etaye.infrastructure.error.InvalidParameterException;
import dev.aymen.etaye.infrastructure.error.ResourceNotFoundException;
import dev.aymen.etaye.infrastructure.transaction.PracticeTransaction;
import dev.aymen.etaye.records.domain.EvidenceRepository;
import dev.aymen.etaye.records.domain.LabResultRepository;
import dev.aymen.etaye.records.domain.LabResultRepository.Analyte;
import dev.aymen.etaye.records.domain.LabResultRepository.Filters;
import dev.aymen.etaye.records.domain.LabResultRepository.Snapshot;
import dev.aymen.etaye.records.domain.LabResultView;
import dev.aymen.etaye.records.dto.LabResultResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
public class LabResultService {

    private static final int LINEAGE_LIMIT = 100;
    private static final Pattern ANALYTE_KEY = Pattern.compile("[0-9a-f]{64}");

    public record Context(UUID practiceId, UUID recordId, UUID actorId, String timezone) {
        private Snapshot at(Instant viewAt) {
            return new Snapshot(practiceId, recordId, viewAt, timezone);
        }
    }

    public record PageRequest(String cursor, Integer limit, Instant asOf) { }
    public record LabPage(List<LabResultResponse> items, String nextCursor, Instant viewAt) { }
    public record AnalytePage(List<Analyte> items, String nextCursor) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RevisionList(List<LabResultResponse> revisions, Boolean truncated) { }

    private record Cursor(Instant viewAt, String after) { }

    private final EvidenceRepository evidence;
    private final LabResultRepository labResults;
    private final PracticeTransaction transaction;
    private final Clock clock;

    public LabResultService(EvidenceRepository evidence, LabResultRepository labResults, PracticeTransaction transaction,
                            ObjectProvider<Clock> clock) {
        this.evidence = evidence;
        this.labResults = labResults;
        this.transaction = transaction;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    public LabPage list(Context context, PageRequest request, Filters filters) {
        int limit = limit(request.limit());
        if (filters.from() != null && filters.to() != null && filters.from().isAfter(filters.to())) {
            throw new InvalidParameterException("from must not be after to");
        }
        if (filters.analyteKey() != null) {
            checkAnalyteKey(filters.analyteKey());
        }
        String binding = binding("lab-results", context, request.asOf(), filters.toString());
        Cursor cursor = cursor(request, binding);
        UUID after = cursor.after() == null ? null : resultId(cursor.after());
        Filters rounded = new Filters(filters.analyteKey(), ceilMicros(filters.from()), ceilMicros(filters.to()));
        return inRecord(context, () -> {
            List<LabResultView> results = labResults.page(context.at(cursor.viewAt()), rounded, after, limit + 1);
            return new LabPage(results.stream().limit(limit).map(LabResultResponse::of).toList(),
                    next(results, limit, cursor, binding, item -> item.id().toString()), cursor.viewAt());
        });
    }

    public AnalytePage analytes(Context context, PageRequest request) {
        int limit = limit(request.limit());
        String binding = binding("lab-analytes", context, request.asOf(), "");
        Cursor cursor = cursor(request, binding);
        if (cursor.after() != null) {
            checkAnalyteKey(cursor.after());
        }
        return inRecord(context, () -> {
            List<Analyte> items = labResults.analytes(context.at(cursor.viewAt()), cursor.after(), limit + 1);
            return new AnalytePage(items.stream().limit(limit).toList(), next(items, limit, cursor, binding, Analyte::key));
        });
    }

    public RevisionList revisions(Context context, UUID resultId, Instant asOf) {
        Snapshot snapshot = context.at(viewInstant(asOf));
        return inRecord(context, () -> {
            List<LabResultView> revisions = labResults.lineage(snapshot, resultId);
            if (revisions.isEmpty()) {
                throw new ResourceNotFoundException("Resource not found");
            }
            if (revisions.size() <= LINEAGE_LIMIT) {
                return new RevisionList(revisions.stream().map(LabResultResponse::of).toList(), null);
            }
            List<LabResultResponse> items = new ArrayList<>(LINEAGE_LIMIT);
            items.add(LabResultResponse.of(revisions.getFirst()));
            revisions.subList(revisions.size() - LINEAGE_LIMIT + 1, revisions.size())
                    .stream().map(LabResultResponse::of).forEach(items::add);
            return new RevisionList(List.copyOf(items), Boolean.TRUE);
        });
    }

    private <T> T inRecord(Context context, java.util.function.Supplier<T> action) {
        return transaction.execute(context.practiceId(), () -> {
            if (!evidence.recordExists(context.practiceId(), context.recordId())) {
                throw new ResourceNotFoundException("Resource not found");
            }
            return action.get();
        });
    }

    private Instant viewInstant(Instant asOf) {
        Instant now = clock.instant();
        if (asOf != null && asOf.isAfter(now)) {
            throw new InvalidParameterException("asOf must not be in the future");
        }
        return (asOf == null ? now : asOf).truncatedTo(ChronoUnit.MICROS);
    }

    private Cursor cursor(PageRequest request, String binding) {
        Instant requestedView = viewInstant(request.asOf());
        String token = request.cursor();
        if (token == null) {
            return new Cursor(requestedView, null);
        }
        if (token.isEmpty() || token.length() > RecordEvidenceService.MAX_CURSOR_LENGTH) {
            throw new InvalidParameterException("malformed cursor");
        }

        try {
            String[] fields = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8).split("\\n", -1);
            if (fields.length != 3 || !fields[2].equals(RecordEvidenceService.sha256(binding + "|" + fields[0] + "|" + fields[1]))) {
                throw new InvalidParameterException("cursor does not belong to this view");
            }
            Instant pinned = Instant.parse(fields[0]);
            if (pinned.isAfter(requestedView) || (request.asOf() != null && !pinned.equals(requestedView))
                    || !pinned.equals(pinned.truncatedTo(ChronoUnit.MICROS))) {
                throw new InvalidParameterException("cursor does not belong to this view");
            }
            return new Cursor(pinned, fields[1]);
        } catch (IllegalArgumentException | DateTimeParseException malformed) {
            throw new InvalidParameterException("malformed cursor");
        }
    }

    private static String binding(String operation, Context context, Instant asOf, String filters) {
        return operation + "|" + context.practiceId() + "|" + context.recordId() + "|" + context.actorId()
                + "|" + context.timezone() + "|" + asOf + "|" + filters;
    }

    private static <T> String next(List<T> items, int limit, Cursor cursor, String binding, Function<T, String> position) {
        if (items.size() <= limit) {
            return null;
        }
        String after = position.apply(items.get(limit - 1));
        String check = RecordEvidenceService.sha256(binding + "|" + cursor.viewAt() + "|" + after);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (cursor.viewAt() + "\n" + after + "\n" + check).getBytes(StandardCharsets.UTF_8));
    }

    private static Instant ceilMicros(Instant instant) {
        if (instant == null) {
            return null;
        }
        Instant floor = instant.truncatedTo(ChronoUnit.MICROS);
        return floor.equals(instant) ? floor : floor.plus(1, ChronoUnit.MICROS);
    }

    private static void checkAnalyteKey(String key) {
        if (!ANALYTE_KEY.matcher(key).matches()) {
            throw new InvalidParameterException("invalid analyte selection");
        }
    }

    private static UUID resultId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            throw new InvalidParameterException("malformed cursor");
        }
    }

    private static int limit(Integer input) {
        if (input != null && (input < 1 || input > RecordEvidenceService.MAX_LIMIT)) {
            throw new InvalidParameterException("limit out of range");
        }
        return input == null ? RecordEvidenceService.DEFAULT_LIMIT : input;
    }
}
