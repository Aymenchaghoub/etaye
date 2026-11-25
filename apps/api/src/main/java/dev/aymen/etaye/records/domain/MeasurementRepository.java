package dev.aymen.etaye.records.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read side of the measurements (story 3.3): immutable revisions, each citing the source revision that supports it. Every
 * statement runs on the connection of the enclosing practice transaction, so forced row-level security applies; the
 * explicit practice and record predicates are the second line, never the only one. The current revision of a lineage at an
 * instant is its highest revision recorded at or before that instant (UD-32), so a later revision is never read.
 */
@Repository
public class MeasurementRepository {

    /** Where a page stops: the observed time, the recording instant and the id of the last item. */
    public record Position(OffsetDateTime observedAt, OffsetDateTime recordedAt, UUID id) {
    }

    /**
     * The most revisions one lineage response lists. A longer lineage keeps its original revision and its newest ones,
     * never the current one, and says it was shortened.
     */
    public static final int MAX_LINEAGE = 100;

    /** The listed revisions of one lineage, oldest first, and whether revisions between the original and the newest are left out. */
    public record Lineage(List<MeasurementView> revisions, boolean truncated) {
    }

    private static final String CURRENT = """
            WITH current_revision AS (
                SELECT DISTINCT ON (m.lineage_id) m.*
                FROM measurement m
                WHERE m.practice_id = :practiceId AND m.record_id = :recordId AND m.recorded_at <= :asOf
                ORDER BY m.lineage_id, m.revision DESC
            )
            """;

    private static final String COLUMNS = """
            c.id, c.practice_id, c.record_id, c.revision, c.concept, c.value_decimal, c.unit, c.observed_at, c.recorded_at,
            c.author_label, c.source_revision_id, c.group_id, c.supersedes_id, c.correction_reason, c.created_at,
            s.reference AS source_reference, s.source_type
            """;

    private static final String SOURCE_JOIN = """
            JOIN source_revision s ON s.practice_id = c.practice_id AND s.record_id = c.record_id AND s.id = c.source_revision_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public MeasurementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static MeasurementView map(ResultSet rs) throws SQLException {
        BigDecimal value = rs.getBigDecimal("value_decimal");
        return new MeasurementView(
                rs.getObject("id", UUID.class),
                rs.getObject("practice_id", UUID.class),
                rs.getObject("record_id", UUID.class),
                rs.getInt("revision"),
                rs.getString("concept"),
                value,
                rs.getString("unit"),
                rs.getObject("observed_at", OffsetDateTime.class).toInstant(),
                rs.getObject("recorded_at", OffsetDateTime.class).toInstant(),
                rs.getString("author_label"),
                rs.getObject("source_revision_id", UUID.class),
                rs.getString("source_reference"),
                SourceType.valueOf(rs.getString("source_type")),
                rs.getObject("group_id", UUID.class),
                rs.getObject("supersedes_id", UUID.class),
                rs.getString("correction_reason"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static MapSqlParameterSource base(UUID practiceId, UUID recordId, Instant asOf) {
        return new MapSqlParameterSource().addValue("practiceId", practiceId).addValue("recordId", recordId)
                .addValue("asOf", utc(asOf));
    }

    /** The position of one current measurement of the view, or empty when the view no longer holds it. */
    public Optional<Position> position(UUID practiceId, UUID recordId, Instant asOf, UUID id) {
        return jdbc.query(CURRENT + "SELECT observed_at, recorded_at, id FROM current_revision WHERE id = :id",
                        base(practiceId, recordId, asOf).addValue("id", id),
                        (rs, row) -> new Position(rs.getObject("observed_at", OffsetDateTime.class),
                                rs.getObject("recorded_at", OffsetDateTime.class), rs.getObject("id", UUID.class)))
                .stream().findFirst();
    }

    /**
     * One page of the current measurements of the view, newest observation first (then newest recording, then id). The
     * bounds apply to the observed time: {@code from} inclusive, {@code to} exclusive.
     */
    public List<MeasurementView> page(UUID practiceId, UUID recordId, Instant asOf, Collection<String> concepts,
                                      Instant from, Instant to, Position after, int limit) {
        StringBuilder where = new StringBuilder("WHERE TRUE");
        MapSqlParameterSource params = base(practiceId, recordId, asOf).addValue("limit", limit);
        if (!concepts.isEmpty()) {
            where.append(" AND c.concept IN (:concepts)");
            params.addValue("concepts", concepts);
        }
        if (from != null) {
            where.append(" AND c.observed_at >= :from");
            params.addValue("from", utc(from));
        }
        if (to != null) {
            where.append(" AND c.observed_at < :to");
            params.addValue("to", utc(to));
        }
        if (after != null) {
            where.append("""
                     AND (c.observed_at < :afterObserved
                        OR (c.observed_at = :afterObserved AND (c.recorded_at < :afterRecorded
                            OR (c.recorded_at = :afterRecorded AND c.id < :afterId))))
                    """);
            params.addValue("afterObserved", after.observedAt())
                    .addValue("afterRecorded", after.recordedAt())
                    .addValue("afterId", after.id());
        }
        String sql = CURRENT + "SELECT " + COLUMNS + " FROM current_revision c " + SOURCE_JOIN + where
                + " ORDER BY c.observed_at DESC, c.recorded_at DESC, c.id DESC LIMIT :limit";
        return jdbc.query(sql, params, (rs, row) -> map(rs));
    }

    /**
     * The revisions of the lineage that contains one measurement, oldest first. With an instant, a revision recorded later
     * is absent, as if it did not exist, and a measurement recorded later is empty (a 404). A measurement of another
     * practice is empty as well. Beyond {@link #MAX_LINEAGE} revisions the original and the newest {@code MAX_LINEAGE - 1}
     * are listed and the result is marked truncated.
     */
    public Optional<Lineage> lineage(UUID practiceId, UUID measurementId, Instant asOf) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("practiceId", practiceId)
                .addValue("id", measurementId);
        String visible = "";
        if (asOf != null) {
            visible = " AND recorded_at <= :asOf";
            params.addValue("asOf", utc(asOf));
        }
        record Anchor(UUID recordId, UUID lineageId) {
        }
        Optional<Anchor> anchor = jdbc.query("SELECT record_id, lineage_id FROM measurement WHERE practice_id = :practiceId AND id = :id"
                                + visible, params,
                        (rs, row) -> new Anchor(rs.getObject("record_id", UUID.class), rs.getObject("lineage_id", UUID.class)))
                .stream().findFirst();
        if (anchor.isEmpty()) {
            return Optional.empty();
        }
        params.addValue("recordId", anchor.get().recordId()).addValue("lineageId", anchor.get().lineageId())
                .addValue("limit", MAX_LINEAGE + 1);
        String scope = "FROM measurement c " + SOURCE_JOIN
                + "WHERE c.practice_id = :practiceId AND c.record_id = :recordId AND c.lineage_id = :lineageId"
                + visible.replace("recorded_at", "c.recorded_at");
        // One more than the maximum tells whether the lineage is longer than what is listed.
        List<MeasurementView> newestFirst = jdbc.query("SELECT " + COLUMNS + " " + scope
                + " ORDER BY c.revision DESC LIMIT :limit", params, (rs, row) -> map(rs));
        boolean truncated = newestFirst.size() > MAX_LINEAGE;
        List<MeasurementView> oldestFirst = new ArrayList<>(truncated ? newestFirst.subList(0, MAX_LINEAGE - 1) : newestFirst);
        Collections.reverse(oldestFirst);
        if (truncated) {
            // The original revision is the value the first correction replaced: it is never the one dropped.
            params.addValue("limit", 1);
            oldestFirst.addAll(0, jdbc.query("SELECT " + COLUMNS + " " + scope + " ORDER BY c.revision ASC LIMIT :limit",
                    params, (rs, row) -> map(rs)));
        }
        return Optional.of(new Lineage(oldestFirst, truncated));
    }
}
