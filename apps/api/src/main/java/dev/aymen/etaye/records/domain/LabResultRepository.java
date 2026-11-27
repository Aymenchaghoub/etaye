package dev.aymen.etaye.records.domain;

import dev.aymen.etaye.infrastructure.error.InvalidParameterException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class LabResultRepository {

    public record Snapshot(UUID practiceId, UUID recordId, Instant viewAt, String timezone) {
    }

    public record Filters(String analyteKey, Instant from, Instant to) {
    }

    public record Analyte(String key, String label) {
    }

    private static final String CURRENT = """
            WITH current_revision AS (
                SELECT DISTINCT ON (lineage_id) *
                FROM lab_result
                WHERE practice_id = :practiceId AND record_id = :recordId AND recorded_at <= :viewAt
                ORDER BY lineage_id, revision DESC
            )
            """;

    private static final String BOUNDED_LINEAGE = """
            WITH anchor AS (
                SELECT lineage_id FROM lab_result
                WHERE practice_id = :practiceId AND record_id = :recordId AND id = :id AND recorded_at <= :viewAt
            ), newest AS (
                SELECT c.* FROM lab_result c JOIN anchor a USING (lineage_id)
                WHERE c.practice_id = :practiceId AND c.record_id = :recordId AND c.recorded_at <= :viewAt
                ORDER BY c.revision DESC LIMIT 101
            ), first_revision AS (
                SELECT c.* FROM lab_result c JOIN anchor a USING (lineage_id)
                WHERE c.practice_id = :practiceId AND c.record_id = :recordId AND c.revision = 1
            ), bounded AS (
                SELECT * FROM newest
                UNION
                SELECT * FROM first_revision
            )
            """;

    private static final String SOURCE = """
            JOIN source_revision s ON s.practice_id = c.practice_id AND s.record_id = c.record_id AND s.id = c.source_revision_id
            """;
    private static final String REVISION_LIST = BOUNDED_LINEAGE
            + "SELECT c.*, s.reference, s.source_type FROM bounded c " + SOURCE + " ORDER BY c.revision";

    private static final String FILTERED = CURRENT + """
            , dated AS (
                SELECT *, COALESCE(specimen_at, specimen_on::timestamp AT TIME ZONE :timezone,
                    result_at, result_on::timestamp AT TIME ZONE :timezone, '-infinity'::timestamptz) AS event_sort
                FROM current_revision
            ), filtered AS (
                SELECT * FROM dated
                WHERE (CAST(:analyteKey AS text) IS NULL OR analyte_key = :analyteKey)
                  AND (CAST(:from AS timestamptz) IS NULL OR event_sort >= :from)
                  AND (CAST(:to AS timestamptz) IS NULL OR
                      (event_sort < :to AND event_sort > '-infinity'::timestamptz))
            )
            """;
    private static final String RESULT_POSITION = FILTERED + "SELECT EXISTS (SELECT 1 FROM filtered WHERE id = :after)";
    private static final String RESULT_PAGE = FILTERED + "SELECT c.*, s.reference, s.source_type FROM filtered c " + SOURCE + """
            WHERE CAST(:after AS uuid) IS NULL OR (c.event_sort, c.recorded_at, c.id) <
                (SELECT event_sort, recorded_at, id FROM filtered WHERE id = :after)
            ORDER BY c.event_sort DESC, c.recorded_at DESC, c.id DESC LIMIT :limit
            """;

    private static final String OPTIONS = CURRENT + """
            , options AS (
                SELECT DISTINCT analyte_key, analyte COLLATE "C" AS label FROM current_revision
            )
            """;
    private static final String OPTION_POSITION = OPTIONS + "SELECT EXISTS (SELECT 1 FROM options WHERE analyte_key = :after)";
    private static final String OPTION_PAGE = OPTIONS + """
            SELECT analyte_key, label FROM options
            WHERE CAST(:after AS text) IS NULL OR (label, analyte_key) >
                (SELECT label, analyte_key FROM options WHERE analyte_key = :after)
            ORDER BY label, analyte_key LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public LabResultRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    /** Latest visible revision first, then filter. The endpoint accepts only an exact, opaque analyte selector. */
    public List<LabResultView> page(Snapshot snapshot, Filters filters, UUID after, int limit) {
        var args = parameters(snapshot).addValue("limit", limit).addValue("after", after)
                .addValue("analyteKey", filters.analyteKey())
                .addValue("from", utc(filters.from())).addValue("to", utc(filters.to()));
        if (after != null) {
            requirePosition(jdbc.queryForObject(RESULT_POSITION, args, Boolean.class));
        }
        return jdbc.query(RESULT_PAGE, args, (rs, row) -> map(rs, snapshot.timezone()));
    }

    public List<Analyte> analytes(Snapshot snapshot, String after, int limit) {
        var args = parameters(snapshot).addValue("limit", limit).addValue("after", after);
        if (after != null) {
            requirePosition(jdbc.queryForObject(OPTION_POSITION, args, Boolean.class));
        }
        return jdbc.query(OPTION_PAGE, args,
                (rs, row) -> new Analyte(rs.getString("analyte_key"), rs.getString("label")));
    }

    /** The original plus enough newest revisions to detect truncation without reading the entire lineage. */
    public List<LabResultView> lineage(Snapshot snapshot, UUID id) {
        return jdbc.query(REVISION_LIST, parameters(snapshot).addValue("id", id),
                (rs, row) -> map(rs, snapshot.timezone()));
    }

    private static void requirePosition(Boolean exists) {
        if (!Boolean.TRUE.equals(exists)) {
            throw new InvalidParameterException("cursor does not belong to this view");
        }
    }

    private static MapSqlParameterSource parameters(Snapshot snapshot) {
        return new MapSqlParameterSource().addValue("practiceId", snapshot.practiceId())
                .addValue("recordId", snapshot.recordId()).addValue("viewAt", utc(snapshot.viewAt()))
                .addValue("timezone", snapshot.timezone());
    }

    private static LabResultView map(ResultSet rs, String timezone) throws SQLException {
        String method = rs.getString("comparison_method");
        var context = method == null ? null
                : new LabResultView.ComparisonContext(method, rs.getString("comparison_fasting"), rs.getString("comparison_window"));
        return new LabResultView(rs.getObject("id", UUID.class), rs.getObject("practice_id", UUID.class),
                rs.getInt("revision"), rs.getString("analyte"),
                rs.getBigDecimal("numeric_value"), rs.getString("qualitative_value"), rs.getString("comparator"),
                rs.getString("unit"), rs.getBigDecimal("lower_bound"), rs.getBigDecimal("upper_bound"),
                rs.getString("reference_interval_text"), rs.getString("interval_source"), rs.getString("interval_unit"),
                rs.getString("interval_context"), rs.getString("outside_interval"), rs.getBoolean("unconfirmed_transcription"),
                date(rs, "specimen", timezone), date(rs, "result", timezone), instant(rs, "recorded_at"),
                instant(rs, "created_at"), rs.getString("author_label"), rs.getObject("source_revision_id", UUID.class),
                rs.getString("reference"), rs.getString("source_type"), rs.getObject("supersedes_id", UUID.class),
                rs.getString("correction_reason"), context);
    }

    private static EventDateView date(ResultSet rs, String prefix, String timezone) throws SQLException {
        Instant at = instant(rs, prefix + "_at");
        if (at != null) {
            return new EventDateView(EventDateView.Precision.INSTANT, at, null, timezone);
        }
        LocalDate on = rs.getObject(prefix + "_on", LocalDate.class);
        return on == null ? new EventDateView(EventDateView.Precision.UNKNOWN, null, null, null)
                : new EventDateView(EventDateView.Precision.DATE, null, on, timezone);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
