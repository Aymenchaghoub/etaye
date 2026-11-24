package dev.aymen.etaye.records.domain;

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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read side of the record evidence: immutable source revisions and per-statement assertions. Every statement runs
 * on the connection of the enclosing practice transaction, so forced row-level security applies; the explicit
 * practice and record predicates are the second line, never the only one. The "current" row of a lineage at an
 * instant is its highest revision recorded at or before that instant (UD-32), so a later revision is never read.
 */
@Repository
public class EvidenceRepository {

    /**
     * Where a page stops: the group (dated or undated), the sort instant, the recording instant and the id of the
     * last item. A cursor carries only the id; the rest is read from the same view.
     */
    public record Position(boolean undated, OffsetDateTime sortAt, OffsetDateTime recordedAt, UUID id) {
    }

    private static final String CURRENT_SOURCES = """
            WITH current_revision AS (
                SELECT DISTINCT ON (s.lineage_id) s.*
                FROM source_revision s
                WHERE s.practice_id = :practiceId AND s.record_id = :recordId AND s.recorded_at <= :asOf
                ORDER BY s.lineage_id, s.revision DESC
            ), shaped AS (
                SELECT c.*, (c.event_precision = 'UNKNOWN') AS undated,
                    CASE c.event_precision
                        WHEN 'INSTANT' THEN c.event_at
                        WHEN 'DATE' THEN (c.event_date::timestamp AT TIME ZONE c.event_timezone)
                    END AS sort_at
                FROM current_revision c
            )
            """;

    private static final String SOURCE_COLUMNS = """
            c.id, c.practice_id, c.record_id, c.revision, c.reference, c.kind, c.label, c.excerpt, c.source_type,
            c.event_precision, c.event_at, c.event_date, c.event_timezone, c.recorded_at, c.author_label, c.locator,
            c.supersedes_id, c.correction_reason, p.reference AS supersedes_reference
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public EvidenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public boolean recordExists(UUID practiceId, UUID recordId) {
        Integer found = jdbc.getJdbcOperations().queryForObject(
                "SELECT count(*) FROM patient_record WHERE practice_id = ? AND id = ?", Integer.class, practiceId, recordId);
        return found != null && found > 0;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static SourceView mapSource(ResultSet rs) throws SQLException {
        return new SourceView(
                rs.getObject("id", UUID.class),
                rs.getObject("practice_id", UUID.class),
                rs.getObject("record_id", UUID.class),
                rs.getInt("revision"),
                rs.getString("reference"),
                SourceKind.valueOf(rs.getString("kind")),
                rs.getString("label"),
                rs.getString("excerpt"),
                SourceType.valueOf(rs.getString("source_type")),
                eventDate(rs),
                rs.getObject("recorded_at", OffsetDateTime.class).toInstant(),
                rs.getString("author_label"),
                rs.getString("locator"),
                rs.getObject("supersedes_id", UUID.class),
                rs.getString("supersedes_reference"),
                rs.getString("correction_reason"));
    }

    private static EventDateView eventDate(ResultSet rs) throws SQLException {
        EventDateView.Precision precision = EventDateView.Precision.valueOf(rs.getString("event_precision"));
        OffsetDateTime at = rs.getObject("event_at", OffsetDateTime.class);
        return new EventDateView(precision, at == null ? null : at.toInstant(),
                rs.getObject("event_date", LocalDate.class), rs.getString("event_timezone"));
    }

    /** The position of one current item of the view, or empty when the view no longer holds it. */
    public Optional<Position> sourcePosition(UUID practiceId, UUID recordId, Instant asOf, UUID id) {
        return jdbc.query(CURRENT_SOURCES + "SELECT undated, sort_at, recorded_at, id FROM shaped WHERE id = :id",
                        base(practiceId, recordId, asOf).addValue("id", id),
                        (rs, row) -> new Position(rs.getBoolean("undated"), rs.getObject("sort_at", OffsetDateTime.class),
                                rs.getObject("recorded_at", OffsetDateTime.class), rs.getObject("id", UUID.class)))
                .stream().findFirst();
    }

    /**
     * One page of the evidence items of the view, newest event first. The undated group is last, ordered by recording
     * time; it is kept under every date filter so that a filter never hides a fact.
     */
    public List<SourceView> pageSources(UUID practiceId, UUID recordId, Instant asOf, Collection<SourceKind> kinds,
                                        Instant from, Instant to, Position after, int limit) {
        StringBuilder where = new StringBuilder("WHERE TRUE");
        MapSqlParameterSource params = base(practiceId, recordId, asOf).addValue("limit", limit);
        if (!kinds.isEmpty()) {
            where.append(" AND c.kind IN (:kinds)");
            params.addValue("kinds", kinds.stream().map(Enum::name).toList());
        }
        if (from != null) {
            where.append(" AND (c.undated OR c.sort_at >= :from)");
            params.addValue("from", utc(from));
        }
        if (to != null) {
            where.append(" AND (c.undated OR c.sort_at < :to)");
            params.addValue("to", utc(to));
        }
        if (after != null) {
            where.append("""
                     AND ((c.undated AND :afterUndated AND (c.recorded_at < :afterRecorded
                            OR (c.recorded_at = :afterRecorded AND c.id < :afterId)))
                        OR (NOT c.undated AND NOT :afterUndated AND (c.sort_at < CAST(:afterSort AS timestamptz)
                            OR (c.sort_at = CAST(:afterSort AS timestamptz) AND (c.recorded_at < :afterRecorded
                                OR (c.recorded_at = :afterRecorded AND c.id < :afterId)))))
                        OR (c.undated AND NOT :afterUndated))
                    """);
            params.addValue("afterUndated", after.undated())
                    .addValue("afterSort", after.sortAt())
                    .addValue("afterRecorded", after.recordedAt())
                    .addValue("afterId", after.id());
        }
        String sql = CURRENT_SOURCES + "SELECT " + SOURCE_COLUMNS + " FROM shaped c "
                + "LEFT JOIN source_revision p ON p.practice_id = c.practice_id AND p.record_id = c.record_id AND p.id = c.supersedes_id "
                + where + " ORDER BY c.undated, c.sort_at DESC, c.recorded_at DESC, c.id DESC LIMIT :limit";
        return jdbc.query(sql, params, (rs, row) -> mapSource(rs));
    }

    /** One revision of the practice. With an instant, a revision recorded later is absent, as if it did not exist. */
    public Optional<SourceView> findSource(UUID practiceId, UUID sourceId, Instant asOf) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("practiceId", practiceId).addValue("id", sourceId);
        String visible = "";
        if (asOf != null) {
            visible = " AND c.recorded_at <= :asOf";
            params.addValue("asOf", utc(asOf));
        }
        return jdbc.query("SELECT " + SOURCE_COLUMNS + """
                         FROM source_revision c
                         LEFT JOIN source_revision p ON p.practice_id = c.practice_id AND p.record_id = c.record_id AND p.id = c.supersedes_id
                         WHERE c.practice_id = :practiceId AND c.id = :id""" + visible,
                params, (rs, row) -> mapSource(rs)).stream().findFirst();
    }

    private static final String CURRENT_ASSERTIONS = """
            WITH current_statement AS (
                SELECT DISTINCT ON (a.lineage_id) a.*
                FROM clinical_assertion a
                WHERE a.practice_id = :practiceId AND a.record_id = :recordId AND a.recorded_at <= :asOf
                ORDER BY a.lineage_id, a.revision DESC
            ), shaped AS (
                SELECT c.*, (c.event_precision = 'UNKNOWN') AS undated,
                    CASE c.event_precision
                        WHEN 'INSTANT' THEN c.event_at
                        WHEN 'DATE' THEN (c.event_date::timestamp AT TIME ZONE c.event_timezone)
                    END AS sort_at
                FROM current_statement c
            )
            """;

    private static final String ASSERTION_FROM = """
            FROM shaped c
            LEFT JOIN source_revision s ON s.practice_id = c.practice_id AND s.record_id = c.record_id AND s.id = c.source_revision_id
            """;

    private static final String ASSERTION_COLUMNS = """
            c.id, c.practice_id, c.category, c.assertion_kind, c.text, c.event_precision, c.event_at, c.event_date,
            c.event_timezone, c.recorded_at, c.revision, c.supersedes_id, c.correction_reason, c.source_revision_id,
            c.created_at, s.reference AS source_reference, s.kind AS source_kind, s.source_type, s.author_label
            """;

    private static AssertionView mapAssertion(ResultSet rs) throws SQLException {
        String sourceType = rs.getString("source_type");
        String sourceKind = rs.getString("source_kind");
        return new AssertionView(
                rs.getObject("id", UUID.class),
                rs.getObject("practice_id", UUID.class),
                AssertionCategory.valueOf(rs.getString("category")),
                AssertionKind.valueOf(rs.getString("assertion_kind")),
                rs.getString("text"),
                eventDate(rs),
                rs.getObject("recorded_at", OffsetDateTime.class).toInstant(),
                rs.getInt("revision"),
                rs.getObject("supersedes_id", UUID.class),
                rs.getString("correction_reason"),
                rs.getObject("source_revision_id", UUID.class),
                rs.getString("source_reference"),
                sourceKind == null ? null : SourceKind.valueOf(sourceKind),
                sourceType == null ? null : SourceType.valueOf(sourceType),
                rs.getString("author_label"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    public Optional<Position> assertionPosition(UUID practiceId, UUID recordId, Instant asOf, UUID id) {
        return jdbc.query(CURRENT_ASSERTIONS + "SELECT undated, sort_at, recorded_at, id FROM shaped WHERE id = :id",
                        base(practiceId, recordId, asOf).addValue("id", id),
                        (rs, row) -> new Position(rs.getBoolean("undated"), rs.getObject("sort_at", OffsetDateTime.class),
                                rs.getObject("recorded_at", OffsetDateTime.class), rs.getObject("id", UUID.class)))
                .stream().findFirst();
    }

    /** One page of the current statements, oldest event first and the undated ones last (ascending keyset). */
    public List<AssertionView> pageAssertions(UUID practiceId, UUID recordId, Instant asOf, AssertionCategory category,
                                              Position after, int limit) {
        StringBuilder where = new StringBuilder("WHERE TRUE");
        MapSqlParameterSource params = base(practiceId, recordId, asOf).addValue("limit", limit);
        if (category != null) {
            where.append(" AND c.category = :category");
            params.addValue("category", category.name());
        }
        if (after != null) {
            where.append("""
                     AND ((NOT c.undated AND NOT :afterUndated AND (c.sort_at > CAST(:afterSort AS timestamptz)
                            OR (c.sort_at = CAST(:afterSort AS timestamptz) AND (c.recorded_at > :afterRecorded
                                OR (c.recorded_at = :afterRecorded AND c.id > :afterId)))))
                        OR (c.undated AND :afterUndated AND (c.recorded_at > :afterRecorded
                            OR (c.recorded_at = :afterRecorded AND c.id > :afterId)))
                        OR (c.undated AND NOT :afterUndated))
                    """);
            params.addValue("afterUndated", after.undated())
                    .addValue("afterSort", after.sortAt())
                    .addValue("afterRecorded", after.recordedAt())
                    .addValue("afterId", after.id());
        }
        String sql = CURRENT_ASSERTIONS + "SELECT " + ASSERTION_COLUMNS + " " + ASSERTION_FROM + where
                + " ORDER BY c.undated, c.sort_at ASC, c.recorded_at ASC, c.id ASC LIMIT :limit";
        return jdbc.query(sql, params, (rs, row) -> mapAssertion(rs));
    }

    /** Every current statement of the view, section by section, oldest event first: the input of the section state. */
    public List<AssertionView> allAssertions(UUID practiceId, UUID recordId, Instant asOf) {
        String sql = CURRENT_ASSERTIONS + "SELECT " + ASSERTION_COLUMNS + " " + ASSERTION_FROM
                + " ORDER BY c.category, c.undated, c.sort_at ASC, c.recorded_at ASC, c.id ASC";
        return jdbc.query(sql, base(practiceId, recordId, asOf), (rs, row) -> mapAssertion(rs));
    }

    private static MapSqlParameterSource base(UUID practiceId, UUID recordId, Instant asOf) {
        return new MapSqlParameterSource().addValue("practiceId", practiceId).addValue("recordId", recordId)
                .addValue("asOf", utc(asOf));
    }
}
