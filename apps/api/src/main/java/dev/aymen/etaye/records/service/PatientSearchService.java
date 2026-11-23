package dev.aymen.etaye.records.service;

import dev.aymen.etaye.infrastructure.error.InvalidParameterException;
import dev.aymen.etaye.infrastructure.transaction.PracticeTransaction;
import dev.aymen.etaye.records.domain.PatientDirectoryRepository;
import dev.aymen.etaye.records.domain.PatientDirectoryRepository.Position;
import dev.aymen.etaye.records.domain.PatientView;
import dev.aymen.etaye.records.dto.PatientPageResponse;
import dev.aymen.etaye.records.dto.PatientResponse;
import dev.aymen.etaye.records.dto.SchedulingPatientPageResponse;
import dev.aymen.etaye.records.dto.SchedulingPatientResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Practice-local patient search for the clinical directory and the scheduling lookup. The query is
 * never logged, audited or echoed; the opaque cursor is bound to the practice, the actor and the
 * normalized query, so a cursor cannot be replayed under another context.
 */
@Service
public class PatientSearchService {

    public static final int DEFAULT_LIMIT = 25;
    public static final int MAX_LIMIT = 100;
    public static final int MAX_QUERY_LENGTH = 100;
    private static final Pattern FRENCH_DATE = Pattern.compile("(?<![\\d/])(\\d{2})/(\\d{2})/(\\d{4})(?![\\d/])");
    private static final Pattern ISO_DATE = Pattern.compile("(?<![\\d-])(\\d{4})-(\\d{2})-(\\d{2})(?![\\d-])");

    private record ParsedQuery(List<String> words, LocalDate birthDate, boolean matchesNothing, String binding) {
    }

    private record Page(List<PatientView> items, String nextCursor) {
    }

    private final PatientDirectoryRepository directory;
    private final PracticeTransaction practiceTransaction;

    public PatientSearchService(PatientDirectoryRepository directory, PracticeTransaction practiceTransaction) {
        this.directory = directory;
        this.practiceTransaction = practiceTransaction;
    }

    public PatientPageResponse searchClinical(UUID practiceId, UUID actorId, String q, String cursor, Integer limit) {
        Page page = search(practiceId, actorId, q, cursor, limit, false);
        return new PatientPageResponse(page.items().stream().map(PatientResponse::of).toList(), page.nextCursor());
    }

    public SchedulingPatientPageResponse searchScheduling(UUID practiceId, UUID actorId, String q, String cursor, Integer limit) {
        Page page = search(practiceId, actorId, q, cursor, limit, true);
        return new SchedulingPatientPageResponse(page.items().stream().map(SchedulingPatientResponse::of).toList(), page.nextCursor());
    }

    private Page search(UUID practiceId, UUID actorId, String q, String cursor, Integer requestedLimit, boolean queryRequired) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidParameterException("limit out of range");
        }
        if (q != null && q.length() > MAX_QUERY_LENGTH) {
            throw new InvalidParameterException("q too long");
        }
        if (queryRequired && (q == null || q.strip().length() < 2)) {
            throw new InvalidParameterException("q must hold at least two characters");
        }
        return practiceTransaction.execute(practiceId, () -> {
            ParsedQuery query = parse(practiceId, actorId, q);
            if (query.matchesNothing()) {
                return new Page(List.of(), null);
            }
            Position after = decode(cursor, query.binding());
            List<PatientView> rows = directory.search(practiceId, query.words(), query.birthDate(), after, limit + 1);
            if (rows.size() <= limit) {
                return new Page(rows, null);
            }
            List<PatientView> items = rows.subList(0, limit);
            PatientView last = items.getLast();
            return new Page(List.copyOf(items), encode(query.binding(), new Position(last.lastNameKey(), last.firstNameKey(), last.id())));
        });
    }

    private ParsedQuery parse(UUID practiceId, UUID actorId, String q) {
        String text = q == null ? "" : q.strip();
        Set<LocalDate> dates = new LinkedHashSet<>();
        String remaining = removeDates(removeDates(text, FRENCH_DATE, true, dates), ISO_DATE, false, dates);
        String normalized = directory.normalize(remaining);
        List<String> words = normalized.isEmpty() ? List.of() : List.of(normalized.split(" "));
        boolean searchable = !words.isEmpty() || !dates.isEmpty();
        // A query without any letter, digit or valid date matches nothing instead of listing everything.
        boolean matchesNothing = dates.size() > 1 || (!text.isEmpty() && !searchable);
        LocalDate birthDate = dates.size() == 1 ? dates.iterator().next() : null;
        String binding = sha256("v1|" + practiceId + "|" + actorId + "|" + String.join(" ", words) + "|" + birthDate);
        return new ParsedQuery(words, birthDate, matchesNothing, binding);
    }

    private static String removeDates(String text, Pattern pattern, boolean dayFirst, Set<LocalDate> dates) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            try {
                LocalDate date = dayFirst
                        ? LocalDate.of(Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(1)))
                        : LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
                dates.add(date);
                matcher.appendReplacement(out, " ");
            } catch (DateTimeException notADate) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String encode(String binding, Position position) {
        String raw = binding + "\n" + position.lastNameKey() + "\n" + position.firstNameKey() + "\n" + position.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Position decode(String cursor, String binding) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\n", -1);
            if (parts.length != 4 || !parts[0].equals(binding)) {
                throw new InvalidParameterException("cursor does not belong to this search");
            }
            return new Position(parts[1], parts[2], UUID.fromString(parts[3]));
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
