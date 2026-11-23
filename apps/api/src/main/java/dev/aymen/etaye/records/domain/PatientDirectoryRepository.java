package dev.aymen.etaye.records.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Search, duplicate lookup and identity update for practice-local patients. Every statement runs on
 * the connection of the enclosing practice transaction, so forced row-level security applies; the
 * explicit practice predicate is the second line, never the only one. Normalization happens in the
 * database (patient_search_key) for stored names and query words alike.
 */
@Repository
public class PatientDirectoryRepository {

    public record Position(String lastNameKey, String firstNameKey, UUID id) {
    }

    private static final String COLUMNS = """
            p.id, p.practice_id, r.id AS record_id, p.first_name, p.last_name, p.date_of_birth,
            p.contact_email, p.phone, p.version, p.created_at, p.updated_at, p.last_name_key, p.first_name_key
            """;
    private static final String FROM = """
            FROM patient p
            JOIN patient_record r ON r.practice_id = p.practice_id AND r.patient_id = p.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PatientDirectoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private static PatientView map(ResultSet rs, int row) throws SQLException {
        return new PatientView(
                rs.getObject("id", UUID.class),
                rs.getObject("practice_id", UUID.class),
                rs.getObject("record_id", UUID.class),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getObject("date_of_birth", LocalDate.class),
                rs.getString("contact_email"),
                rs.getString("phone"),
                rs.getLong("version"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                rs.getString("last_name_key"),
                rs.getString("first_name_key"));
    }

    /** The key stored for a name: its normalized words, or its lowercase text when none remain. */
    public String normalizeName(String text) {
        String key = jdbc.getJdbcOperations().queryForObject("SELECT patient_name_key(?)", String.class, text);
        return key == null ? "" : key;
    }

    /** The current order position of a patient of this practice, the only state a cursor carries. */
    public Optional<Position> positionOf(UUID practiceId, UUID patientId) {
        return jdbc.query("SELECT p.last_name_key, p.first_name_key, p.id FROM patient p WHERE p.practice_id = :practiceId AND p.id = :id",
                new MapSqlParameterSource().addValue("practiceId", practiceId).addValue("id", patientId),
                (rs, row) -> new Position(rs.getString("last_name_key"), rs.getString("first_name_key"), rs.getObject("id", UUID.class)))
                .stream().findFirst();
    }

    /**
     * Serializes concurrent registrations of the same likely identity in one practice, so that the
     * duplicate check and the insert of two requests cannot interleave. Two transaction-scoped locks
     * cover the two clauses of the rule and are taken in key order to avoid a deadlock.
     */
    public void lockIdentity(UUID practiceId, String firstKey, String lastKey, LocalDate birthDate) {
        if (firstKey.isEmpty() || lastKey.isEmpty()) {
            return;
        }
        String names = firstKey.compareTo(lastKey) <= 0 ? firstKey + "|" + lastKey : lastKey + "|" + firstKey;
        long[] keys = {lockKey(practiceId + "|names|" + names), lockKey(practiceId + "|birth|" + birthDate + "|" + lastKey)};
        java.util.Arrays.sort(keys);
        for (long key : keys) {
            jdbc.getJdbcOperations().queryForList("SELECT pg_advisory_xact_lock(?)", key);
        }
    }

    private static long lockKey(String text) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.nio.ByteBuffer.wrap(hash).getLong();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Moves the record version with the identity it embeds, so a conditional record read never serves a stale patient. */
    public void touchRecord(UUID practiceId, UUID recordId) {
        jdbc.update("UPDATE patient_record SET version = version + 1, updated_at = now() WHERE practice_id = :practiceId AND id = :id",
                new MapSqlParameterSource().addValue("practiceId", practiceId).addValue("id", recordId));
    }

    /** The database normalization of a free text, empty when it holds no letter or digit. */
    public String normalize(String text) {
        String key = jdbc.getJdbcOperations().queryForObject("SELECT patient_search_key(?)", String.class, text);
        return key == null ? "" : key;
    }

    /**
     * Patients matching every word at the start of a normalized name word and, when given, one birth
     * date, ordered by normalized last name, first name and id after the cursor position.
     */
    public List<PatientView> search(UUID practiceId, List<String> words, LocalDate birthDate, Position after, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("practiceId", practiceId).addValue("limit", limit);
        StringBuilder where = new StringBuilder("WHERE p.practice_id = :practiceId");
        if (birthDate != null) {
            where.append(" AND p.date_of_birth = :birthDate");
            params.addValue("birthDate", birthDate);
        }
        for (int i = 0; i < words.size(); i++) {
            // Words hold only letters and digits, so no LIKE metacharacter can reach the pattern.
            where.append(" AND (' ' || p.last_name_key || ' ' || p.first_name_key) LIKE :w").append(i);
            params.addValue("w" + i, "% " + words.get(i) + "%");
        }
        if (after != null) {
            where.append(" AND (p.last_name_key, p.first_name_key, p.id) > (:afterLast, :afterFirst, :afterId)");
            params.addValue("afterLast", after.lastNameKey())
                    .addValue("afterFirst", after.firstNameKey())
                    .addValue("afterId", after.id());
        }
        return jdbc.query("SELECT " + COLUMNS + FROM + where
                + " ORDER BY p.last_name_key, p.first_name_key, p.id LIMIT :limit", params, PatientRepositoryMapper.INSTANCE);
    }

    /** Likely duplicates under the rule of the contract guide, in search order. */
    public List<PatientView> findDuplicateCandidates(UUID practiceId, String firstKey, String lastKey, LocalDate birthDate, int limit) {
        if (firstKey.isEmpty() || lastKey.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("practiceId", practiceId)
                .addValue("f", firstKey)
                .addValue("l", lastKey)
                .addValue("birthDate", birthDate)
                .addValue("limit", limit);
        return jdbc.query("SELECT " + COLUMNS + FROM + """
                WHERE p.practice_id = :practiceId
                  AND ((p.first_name_key = :f AND p.last_name_key = :l)
                    OR (p.first_name_key = :l AND p.last_name_key = :f)
                    OR (p.date_of_birth = :birthDate AND p.last_name_key = :l))
                ORDER BY p.last_name_key, p.first_name_key, p.id
                LIMIT :limit
                """, params, PatientRepositoryMapper.INSTANCE);
    }

    public Optional<PatientView> findView(UUID practiceId, UUID patientId) {
        return jdbc.query("SELECT " + COLUMNS + FROM + " WHERE p.practice_id = :practiceId AND p.id = :id",
                new MapSqlParameterSource().addValue("practiceId", practiceId).addValue("id", patientId),
                PatientRepositoryMapper.INSTANCE).stream().findFirst();
    }

    /** The current version of a patient, locking its row against a concurrent correction. */
    public Optional<Long> lockVersion(UUID practiceId, UUID patientId) {
        return jdbc.query("SELECT p.version FROM patient p WHERE p.practice_id = :practiceId AND p.id = :id FOR UPDATE",
                new MapSqlParameterSource().addValue("practiceId", practiceId).addValue("id", patientId),
                (rs, row) -> rs.getLong("version")).stream().findFirst();
    }

    /** Applies a correction when the version still matches; false means the guard no longer holds. */
    public boolean updateIdentity(UUID practiceId, UUID patientId, long expectedVersion, String firstName, String lastName,
                                  LocalDate birthDate, String email, String phone) {
        return jdbc.update("""
                UPDATE patient SET first_name = :firstName, last_name = :lastName, date_of_birth = :birthDate,
                    contact_email = :email, phone = :phone, version = version + 1, updated_at = now()
                WHERE practice_id = :practiceId AND id = :id AND version = :expected
                """, new MapSqlParameterSource()
                .addValue("firstName", firstName).addValue("lastName", lastName).addValue("birthDate", birthDate)
                .addValue("email", email).addValue("phone", phone).addValue("practiceId", practiceId)
                .addValue("id", patientId).addValue("expected", expectedVersion)) == 1;
    }

    private enum PatientRepositoryMapper implements org.springframework.jdbc.core.RowMapper<PatientView> {
        INSTANCE;

        @Override
        public PatientView mapRow(ResultSet rs, int rowNum) throws SQLException {
            return map(rs, rowNum);
        }
    }
}
