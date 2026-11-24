package dev.aymen.etaye.records;

import dev.aymen.etaye.AbstractPostgresTest;
import dev.aymen.etaye.TestSessionHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The record evidence reads on records created inside the test stack: the cases the synthetic fixture cannot hold
 * (a member with CLINICAL_READ alone, a record of more than 25 items, an event without date, long untrusted text),
 * the database guarantees (read-only runtime role, immutability, constraints, row-level security) and the isolation
 * and denial behavior of the five read operations (C1, C5 to C8, C11, C12, C15).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecordEvidenceHttpTest extends AbstractPostgresTest {

    @LocalServerPort private int port;
    @Autowired private TestSessionHelper sessions;

    private UUID practiceA;
    private UUID practiceB;
    private UUID recordA;
    private UUID recordB;
    private String clinicalA;
    private String readOnlyA;
    private String adminOnlyA;
    private String clinicalB;
    private UUID clinicalAAccount;

    @BeforeEach
    void fixtures() throws SQLException {
        practiceA = createPracticeAsOwner("Cabinet A " + UUID.randomUUID());
        practiceB = createPracticeAsOwner("Cabinet B " + UUID.randomUUID());
        clinicalAAccount = createAccountAsOwner("a." + UUID.randomUUID() + "@example.test", "Dr A");
        createMembershipAsOwner(clinicalAAccount, practiceA, "ACTIVE", new String[]{"CLINICAL_READ", "CLINICAL_WRITE"});
        clinicalA = sessions.createSessionCookie(clinicalAAccount);
        UUID readOnly = createAccountAsOwner("ro." + UUID.randomUUID() + "@example.test", "Dr Lecture");
        createMembershipAsOwner(readOnly, practiceA, "ACTIVE", new String[]{"CLINICAL_READ"});
        readOnlyA = sessions.createSessionCookie(readOnly);
        UUID admin = createAccountAsOwner("adm." + UUID.randomUUID() + "@example.test", "Admin");
        createMembershipAsOwner(admin, practiceA, "ACTIVE", new String[]{"TEAM_MANAGE", "PRACTICE_SETTINGS", "ADMIN_AUDIT"});
        adminOnlyA = sessions.createSessionCookie(admin);
        UUID clinicianB = createAccountAsOwner("b." + UUID.randomUUID() + "@example.test", "Dr B");
        createMembershipAsOwner(clinicianB, practiceB, "ACTIVE", new String[]{"CLINICAL_READ"});
        clinicalB = sessions.createSessionCookie(clinicianB);
        recordA = createRecord(practiceA, "Alice", "Évidence");
        recordB = createRecord(practiceB, "Bruno", "Ailleurs");
    }

    // ---------------------------------------------------------------- setup helpers (owner connection)

    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
    }

    private UUID createRecord(UUID practice, String first, String last) throws SQLException {
        UUID patient = UUID.randomUUID();
        UUID record = UUID.randomUUID();
        try (Connection c = owner()) {
            c.setAutoCommit(false);
            scope(c, practice);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth) VALUES (?, ?, ?, ?, '1980-01-01')")) {
                ps.setObject(1, patient);
                ps.setObject(2, practice);
                ps.setString(3, first);
                ps.setString(4, last);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO patient_record (id, practice_id, patient_id) VALUES (?, ?, ?)")) {
                ps.setObject(1, record);
                ps.setObject(2, practice);
                ps.setObject(3, patient);
                ps.executeUpdate();
            }
            c.commit();
        }
        return record;
    }

    private static void scope(Connection c, UUID practice) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT set_config('etaye.practice_id', ?, true)")) {
            ps.setString(1, practice.toString());
            ps.execute();
        }
    }

    /** One source revision. {@code event} is "I:instant", "D:date" or "U" for an unknown date. */
    private UUID source(UUID practice, UUID record, String reference, String kind, String label, String excerpt,
                        String event, String recordedAt) throws SQLException {
        return source(practice, record, UUID.randomUUID(), 1, null, null, reference, kind, label, excerpt, event, recordedAt);
    }

    private UUID source(UUID practice, UUID record, UUID lineage, int revision, UUID supersedes, String reason,
                        String reference, String kind, String label, String excerpt, String event, String recordedAt)
            throws SQLException {
        UUID id = UUID.randomUUID();
        String precision = event.startsWith("I:") ? "INSTANT" : event.startsWith("D:") ? "DATE" : "UNKNOWN";
        try (Connection c = owner()) {
            c.setAutoCommit(false);
            scope(c, practice);
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO source_revision (id, practice_id, record_id, lineage_id, revision, reference, kind, label,
                        excerpt, source_type, event_precision, event_at, event_date, event_timezone, recorded_at,
                        author_label, supersedes_id, correction_reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PRACTITIONER_OBSERVED', ?, CAST(? AS timestamptz), CAST(? AS date), ?,
                        CAST(? AS timestamptz), 'Dr Test', ?, ?)
                    """)) {
                ps.setObject(1, id);
                ps.setObject(2, practice);
                ps.setObject(3, record);
                ps.setObject(4, lineage);
                ps.setInt(5, revision);
                ps.setString(6, reference);
                ps.setString(7, kind);
                ps.setString(8, label);
                ps.setString(9, excerpt);
                ps.setString(10, precision);
                ps.setString(11, "INSTANT".equals(precision) ? event.substring(2) : null);
                ps.setString(12, "DATE".equals(precision) ? event.substring(2) : null);
                ps.setString(13, "UNKNOWN".equals(precision) ? null : "Europe/Paris");
                ps.setString(14, recordedAt);
                ps.setObject(15, supersedes);
                ps.setString(16, reason);
                ps.executeUpdate();
            }
            c.commit();
        }
        return id;
    }

    private void assertion(UUID practice, UUID record, String category, String kind, String text, UUID source,
                           String recordedAt) throws SQLException {
        try (Connection c = owner()) {
            c.setAutoCommit(false);
            scope(c, practice);
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO clinical_assertion (id, practice_id, record_id, lineage_id, revision, category, assertion_kind,
                        text, event_precision, source_revision_id, recorded_at)
                    VALUES (?, ?, ?, ?, 1, ?, ?, ?, 'UNKNOWN', ?, CAST(? AS timestamptz))
                    """)) {
                UUID id = UUID.randomUUID();
                ps.setObject(1, id);
                ps.setObject(2, practice);
                ps.setObject(3, record);
                ps.setObject(4, id);
                ps.setString(5, category);
                ps.setString(6, kind);
                ps.setString(7, text);
                ps.setObject(8, source);
                ps.setString(9, recordedAt);
                ps.executeUpdate();
            }
            c.commit();
        }
    }

    // ---------------------------------------------------------------- HTTP helpers

    private ResponseEntity<Map<String, Object>> get(String cookie, String path) {
        var call = RestClient.create().get().uri(java.net.URI.create("http://localhost:" + port + "/api/v1" + path));
        if (cookie != null) call.header(HttpHeaders.COOKIE, cookie);
        return call.retrieve().onStatus(status -> true, (request, response) -> {})
                .toEntity(new ParameterizedTypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return (List<Map<String, Object>>) response.getBody().get("items");
    }

    private static List<String> references(ResponseEntity<Map<String, Object>> response) {
        return items(response).stream().map(item -> item.get("reference").toString()).toList();
    }

    private static Map<String, Object> withoutCorrelation(Map<String, Object> problem) {
        var copy = new HashMap<>(problem);
        copy.remove("correlationId");
        return copy;
    }

    private String timeline(UUID practice, UUID record) {
        return "/practices/" + practice + "/records/" + record + "/timeline";
    }

    // ---------------------------------------------------------------- C5, C8: unknown dates and filters

    @Test
    @DisplayName("C5, C8: an unknown event date forms a last group, is never placed at its recording date and survives a date filter")
    void undatedItemsFormALastGroupAndStayUnderADateFilter() throws SQLException {
        source(practiceA, recordA, "SRC-X-1", "CONSULTATION_NOTE", "Daté à l'instant", "Texte 1", "I:2026-10-01T09:00:00+02:00", "2026-10-02T10:00:00+02:00");
        source(practiceA, recordA, "SRC-X-2", "CONSULTATION_NOTE", "Date seule", "Texte 2", "D:2026-09-30", "2026-10-03T10:00:00+02:00");
        source(practiceA, recordA, "SRC-X-3", "UPLOADED_DOCUMENT", "Sans date récente", "Texte 3", "U", "2026-10-05T10:00:00+02:00");
        source(practiceA, recordA, "SRC-X-4", "UPLOADED_DOCUMENT", "Sans date ancienne", "Texte 4", "U", "2026-10-04T10:00:00+02:00");

        var all = get(clinicalA, timeline(practiceA, recordA));
        assertThat(references(all)).containsExactly("SRC-X-1", "SRC-X-2", "SRC-X-3", "SRC-X-4");
        var byRef = new HashMap<String, Map<String, Object>>();
        items(all).forEach(item -> byRef.put(item.get("reference").toString(), item));
        // An undated item shows no instant and no date; its recording date is a separate field.
        assertThat(byRef.get("SRC-X-3").get("eventDate")).isEqualTo(Map.of("precision", "UNKNOWN"));
        assertThat(byRef.get("SRC-X-3").get("recordedAt").toString()).startsWith("2026-10-05T08:00");
        // A date-only event carries a date and its time zone, never an invented time.
        assertThat(byRef.get("SRC-X-2").get("eventDate")).isEqualTo(Map.of("precision", "DATE", "date", "2026-09-30", "timezone", "Europe/Paris"));

        // C8: with a date filter the undated group stays listed (Aymen, UD-68).
        var filtered = get(clinicalA, timeline(practiceA, recordA) + "?from=2026-10-01T00:00:00%2B02:00");
        assertThat(references(filtered)).containsExactly("SRC-X-1", "SRC-X-3", "SRC-X-4");
        var window = get(clinicalA, timeline(practiceA, recordA) + "?from=2026-09-30T00:00:00%2B02:00&to=2026-10-01T00:00:00%2B02:00");
        assertThat(references(window)).containsExactly("SRC-X-2", "SRC-X-3", "SRC-X-4");
        var kind = get(clinicalA, timeline(practiceA, recordA) + "?kind=UPLOADED_DOCUMENT&from=2027-01-01T00:00:00Z");
        assertThat(references(kind)).containsExactly("SRC-X-3", "SRC-X-4");

        // The cursor crosses the group boundary one item at a time.
        List<String> walked = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 6; page++) {
            var response = get(clinicalA, timeline(practiceA, recordA) + "?limit=1" + (cursor == null ? "" : "&cursor=" + cursor));
            walked.addAll(references(response));
            cursor = (String) response.getBody().get("nextCursor");
            if (cursor == null) break;
        }
        assertThat(walked).containsExactly("SRC-X-1", "SRC-X-2", "SRC-X-3", "SRC-X-4");
    }

    // ---------------------------------------------------------------- C7: pagination

    @Test
    @DisplayName("C7: 25 items by default, 100 at most, an opaque cursor and no total")
    void largeRecordsPageAtTwentyFive() throws SQLException {
        for (int i = 0; i < 30; i++) {
            source(practiceA, recordA, "SRC-L-%02d".formatted(i), "CONSULTATION_NOTE", "Note " + i, "Texte " + i,
                    "I:2026-09-%02dT09:00:00+02:00".formatted(i % 28 + 1), "2026-10-02T10:00:00+02:00");
        }
        var first = get(clinicalA, timeline(practiceA, recordA));
        assertThat(items(first)).hasSize(25);
        assertThat(first.getBody()).doesNotContainKeys("total", "count", "totalCount");
        String cursor = (String) first.getBody().get("nextCursor");
        assertThat(cursor).isNotBlank().doesNotContain("SRC-L");
        var second = get(clinicalA, timeline(practiceA, recordA) + "?cursor=" + cursor);
        assertThat(items(second)).hasSize(5);
        assertThat(second.getBody().get("nextCursor")).isNull();
        var all = get(clinicalA, timeline(practiceA, recordA) + "?limit=100");
        assertThat(items(all)).hasSize(30);
        assertThat(items(get(clinicalA, "/practices/" + practiceA + "/records/" + recordA + "/sources?limit=100"))).hasSize(30);
        // Pages never repeat an item.
        var seen = new java.util.HashSet<String>();
        items(first).forEach(i -> seen.add(i.get("reference").toString()));
        items(second).forEach(i -> assertThat(seen.add(i.get("reference").toString())).isTrue());
    }

    // ---------------------------------------------------------------- C11, C12: read-only member, untrusted text

    @Test
    @DisplayName("C11, C12: a member with CLINICAL_READ alone reads everything, and long untrusted text comes back as written")
    void readOnlyMemberReadsLongUntrustedText() throws SQLException {
        String hostile = "<script>alert('x')</script> <img src=x onerror=1> & \"guillemets\" ’ œ — " + "é".repeat(20);
        String longWord = "A".repeat(70);
        String longStatement = ("Texte long " + longWord + " ").repeat(80).substring(0, 5000);
        String longLabel = ("Libellé " + longWord + " ").repeat(30).substring(0, 500);
        UUID src = source(practiceA, recordA, "SRC-H-1", "CONSULTATION_NOTE", longLabel, longStatement,
                "I:2026-10-01T09:00:00+02:00", "2026-10-02T10:00:00+02:00");
        assertion(practiceA, recordA, "ALLERGY", "PRESENT", hostile, src, "2026-10-02T10:00:00+02:00");

        String base = "/practices/" + practiceA + "/records/" + recordA;
        var timeline = get(readOnlyA, base + "/timeline");
        assertThat(items(timeline).getFirst().get("label")).isEqualTo(longLabel);
        var source = get(readOnlyA, "/practices/" + practiceA + "/sources/" + items(timeline).getFirst().get("sourceRevisionId"));
        assertThat(source.getBody().get("excerpt")).isEqualTo(longStatement);
        assertThat(((String) source.getBody().get("excerpt"))).hasSize(5000);
        var history = get(readOnlyA, base + "/history");
        assertThat(history.getStatusCode().value()).isEqualTo(200);
        assertThat(history.getBody().toString()).contains(hostile.substring(0, 40));
        // The text is returned as JSON data, never interpreted: the response is JSON and no-store.
        assertThat(history.getHeaders().getContentType().toString()).startsWith("application/json");
        assertThat(get(readOnlyA, base + "/assertions").getStatusCode().value()).isEqualTo(200);
        assertThat(get(readOnlyA, base + "/sources").getStatusCode().value()).isEqualTo(200);
    }

    // ---------------------------------------------------------------- C1: isolation and denials

    @Test
    @DisplayName("C1: another practice's record and source are 404 like an absent one, denials are audited, reused connections never leak")
    void isolationDenialsAndConnectionReuse() throws SQLException {
        UUID sourceA = source(practiceA, recordA, "SRC-A", "CONSULTATION_NOTE", "Note A", "Texte A", "I:2026-10-01T09:00:00+02:00", "2026-10-02T10:00:00+02:00");
        UUID sourceB = source(practiceB, recordB, "SRC-B", "CONSULTATION_NOTE", "Note B", "Texte B", "I:2026-10-01T09:00:00+02:00", "2026-10-02T10:00:00+02:00");
        assertion(practiceB, recordB, "ALLERGY", "PRESENT", "Allergie de B", sourceB, "2026-10-02T10:00:00+02:00");

        // A's clinician through A's route to B's record, and to a guessed B source: the same 404 as an absent one.
        var foreign = get(clinicalA, timeline(practiceA, recordB));
        var absent = get(clinicalA, timeline(practiceA, UUID.randomUUID()));
        assertThat(foreign.getStatusCode().value()).isEqualTo(404);
        assertThat(withoutCorrelation(foreign.getBody())).isEqualTo(withoutCorrelation(absent.getBody()));
        for (String suffix : List.of("/timeline", "/sources", "/assertions", "/history")) {
            assertThat(get(clinicalA, "/practices/" + practiceA + "/records/" + recordB + suffix).getStatusCode().value()).as(suffix).isEqualTo(404);
            // The route practice never grants authority: B's record through B's route needs B's membership.
            assertThat(get(clinicalA, "/practices/" + practiceB + "/records/" + recordB + suffix).getStatusCode().value()).as("B" + suffix).isEqualTo(404);
        }
        var guessed = get(clinicalA, "/practices/" + practiceA + "/sources/" + sourceB);
        var unknown = get(clinicalA, "/practices/" + practiceA + "/sources/" + UUID.randomUUID());
        assertThat(guessed.getStatusCode().value()).isEqualTo(404);
        assertThat(withoutCorrelation(guessed.getBody())).isEqualTo(withoutCorrelation(unknown.getBody()));
        assertThat(get(clinicalB, "/practices/" + practiceB + "/sources/" + sourceA).getStatusCode().value()).isEqualTo(404);
        assertThat(get(clinicalB, "/practices/" + practiceB + "/sources/" + sourceB).getStatusCode().value()).isEqualTo(200);

        // 403 for the administrator without a clinical capability, and each denial writes exactly one security event.
        var forbidden = get(adminOnlyA, timeline(practiceA, recordA));
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(deniedEvents(forbidden.getBody().get("correlationId").toString())).isEqualTo(1);
        // The denial audit is bounded per actor, so the hidden-practice case uses an actor with no earlier denial.
        var hidden = get(readOnlyA, timeline(practiceB, recordB));
        assertThat(hidden.getStatusCode().value()).isEqualTo(404);
        assertThat(deniedEvents(hidden.getBody().get("correlationId").toString())).isEqualTo(1);

        // Pooled connections are reused across practices: each actor only ever sees its own evidence.
        for (int round = 0; round < 12; round++) {
            assertThat(references(get(clinicalA, timeline(practiceA, recordA)))).containsExactly("SRC-A");
            assertThat(references(get(clinicalB, timeline(practiceB, recordB)))).containsExactly("SRC-B");
        }
    }

    private int deniedEvents(String correlationId) throws SQLException {
        try (Connection c = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword());
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM audit_event WHERE correlation_id = ? AND event_type = 'ACCESS_DENIED'")) {
            ps.setString(1, correlationId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // ---------------------------------------------------------------- C15: no write, no record change

    @Test
    @DisplayName("C15: reads change nothing, and the runtime role cannot write, update or delete evidence")
    void readsAreSideEffectFreeAndTheRuntimeRoleIsReadOnly() throws SQLException {
        UUID src = source(practiceA, recordA, "SRC-R", "CONSULTATION_NOTE", "Note", "Texte", "I:2026-10-01T09:00:00+02:00", "2026-10-02T10:00:00+02:00");
        assertion(practiceA, recordA, "ALLERGY", "PRESENT", "Texte", src, "2026-10-02T10:00:00+02:00");
        String before = recordState();
        int auditBefore = auditRows();
        String base = "/practices/" + practiceA + "/records/" + recordA;
        for (String suffix : List.of("/timeline", "/sources", "/assertions", "/history")) {
            assertThat(get(clinicalA, base + suffix).getStatusCode().value()).isEqualTo(200);
        }
        assertThat(get(clinicalA, "/practices/" + practiceA + "/sources/" + src).getStatusCode().value()).isEqualTo(200);
        assertThat(recordState()).isEqualTo(before);
        // D-62: routine reads are not audited.
        assertThat(auditRows()).isEqualTo(auditBefore);

        try (Connection runtime = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), RUNTIME_USER, RUNTIME_PASSWORD);
             Statement st = runtime.createStatement()) {
            st.execute("SELECT set_config('etaye.practice_id', '" + practiceA + "', false)");
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM source_revision")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            for (String sql : List.of(
                    "INSERT INTO source_revision (practice_id, record_id, lineage_id, revision, reference, kind, label, source_type, event_precision, recorded_at, author_label) "
                            + "VALUES ('" + practiceA + "', '" + recordA + "', gen_random_uuid(), 1, 'X', 'CONSULTATION_NOTE', 'x', 'PATIENT_REPORTED', 'UNKNOWN', now(), 'x')",
                    "UPDATE source_revision SET label = 'altéré'",
                    "DELETE FROM source_revision",
                    "UPDATE clinical_assertion SET text = 'altéré'",
                    "DELETE FROM clinical_assertion",
                    "TRUNCATE source_revision")) {
                assertThatThrownBy(() -> st.execute(sql)).as(sql).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            }
            // Row-level security: another practice's scope sees none of it, and neither does no scope at all.
            st.execute("SELECT set_config('etaye.practice_id', '" + practiceB + "', false)");
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM source_revision")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
            st.execute("SELECT set_config('etaye.practice_id', '', false)");
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM clinical_assertion")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
        // The identity role has no access to evidence either.
        try (Connection identity = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), IDENTITY_USER, IDENTITY_PASSWORD);
             Statement st = identity.createStatement()) {
            assertThatThrownBy(() -> st.execute("SELECT * FROM source_revision")).hasMessageContaining("permission denied");
        }
    }

    private String recordState() throws SQLException {
        try (Connection c = owner(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            scope(c, practiceA);
            try (ResultSet rs = st.executeQuery("SELECT record_revision || '/' || version || '/' || updated_at FROM patient_record WHERE id = '" + recordA + "'")) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            } finally {
                c.rollback();
            }
        }
    }

    private int auditRows() throws SQLException {
        try (Connection c = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword());
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM audit_event WHERE event_type <> 'ACCESS_DENIED'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // ---------------------------------------------------------------- database integrity

    @Test
    @DisplayName("Evidence integrity: immutable rows, no fabricated date, supersession inside the record and lineage")
    void databaseConstraintsProtectTheEvidence() throws SQLException {
        UUID lineage = UUID.randomUUID();
        UUID first = source(practiceA, recordA, lineage, 1, null, null, "SRC-C-1", "PRACTITIONER_MEASUREMENT", "Poids 86,4 kg", "Poids saisi",
                "I:2026-10-20T10:12:00+02:00", "2026-10-20T10:13:00+02:00");
        UUID second = source(practiceA, recordA, lineage, 2, first, "Erreur de saisie", "SRC-C-2", "PRACTITIONER_MEASUREMENT", "Poids 68,4 kg",
                "Poids corrigé", "I:2026-10-20T10:12:00+02:00", "2026-10-20T10:15:00+02:00");
        // A superseding revision needs its reason, its predecessor and the same lineage and record.
        assertThatThrownBy(() -> source(practiceA, recordA, lineage, 3, second, null, "SRC-C-3", "PRACTITIONER_MEASUREMENT", "x", "x",
                "I:2026-10-20T10:12:00+02:00", "2026-10-20T10:16:00+02:00")).isInstanceOf(SQLException.class).hasMessageContaining("ck_source_revision_correction");
        assertThatThrownBy(() -> source(practiceA, recordA, UUID.randomUUID(), 2, second, "Autre lignée", "SRC-C-4", "PRACTITIONER_MEASUREMENT", "x", "x",
                "I:2026-10-20T10:12:00+02:00", "2026-10-20T10:16:00+02:00")).isInstanceOf(SQLException.class).hasMessageContaining("fk_source_revision_supersedes");
        UUID otherRecord = createRecord(practiceA, "Autre", "Dossier");
        assertThatThrownBy(() -> source(practiceA, otherRecord, lineage, 3, second, "Autre dossier", "SRC-C-5", "PRACTITIONER_MEASUREMENT", "x", "x",
                "I:2026-10-20T10:12:00+02:00", "2026-10-20T10:16:00+02:00")).isInstanceOf(SQLException.class).hasMessageContaining("fk_source_revision_supersedes");
        assertThatThrownBy(() -> source(practiceA, recordA, UUID.randomUUID(), 1, null, null, "SRC-C-6", "UNKNOWN_KIND", "x", "x", "U",
                "2026-10-20T10:16:00+02:00")).isInstanceOf(SQLException.class).hasMessageContaining("ck_source_revision_kind");
        // The same lineage cannot hold two rows of one revision number.
        assertThatThrownBy(() -> source(practiceA, recordA, lineage, 2, first, "Doublon", "SRC-C-7", "PRACTITIONER_MEASUREMENT", "x", "x",
                "I:2026-10-20T10:12:00+02:00", "2026-10-20T10:17:00+02:00")).isInstanceOf(SQLException.class).hasMessageContaining("uq_source_revision_lineage_revision");

        try (Connection c = owner(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            scope(c, practiceA);
            // An unknown event date cannot carry a fabricated instant; a date-only event needs its zone.
            assertThatThrownBy(() -> st.execute("""
                    INSERT INTO source_revision (practice_id, record_id, lineage_id, revision, reference, kind, label, source_type,
                        event_precision, event_at, recorded_at, author_label)
                    VALUES ('%s', '%s', gen_random_uuid(), 1, 'F', 'CONSULTATION_NOTE', 'x', 'PATIENT_REPORTED', 'UNKNOWN', now(), now(), 'x')
                    """.formatted(practiceA, recordA))).hasMessageContaining("ck_source_revision_event");
            c.rollback();
            scope(c, practiceA);
            assertThatThrownBy(() -> st.execute("""
                    INSERT INTO source_revision (practice_id, record_id, lineage_id, revision, reference, kind, label, source_type,
                        event_precision, event_date, recorded_at, author_label)
                    VALUES ('%s', '%s', gen_random_uuid(), 1, 'F', 'CONSULTATION_NOTE', 'x', 'PATIENT_REPORTED', 'DATE', '2026-10-01', now(), 'x')
                    """.formatted(practiceA, recordA))).hasMessageContaining("ck_source_revision_event");
            c.rollback();
            scope(c, practiceA);
            // Immutable even for the owner.
            assertThatThrownBy(() -> st.execute("UPDATE source_revision SET label = 'altéré' WHERE id = '" + first + "'"))
                    .hasMessageContaining("immutable");
            c.rollback();
            scope(c, practiceA);
            assertThatThrownBy(() -> st.execute("DELETE FROM source_revision WHERE id = '" + second + "'")).hasMessageContaining("immutable");
            c.rollback();
            scope(c, practiceA);
            // An assertion without a source may only be UNKNOWN or NOT_ASKED, and its source stays in its own record.
            assertThatThrownBy(() -> st.execute("""
                    INSERT INTO clinical_assertion (practice_id, record_id, lineage_id, revision, category, assertion_kind, text, event_precision, recorded_at)
                    VALUES ('%s', '%s', gen_random_uuid(), 1, 'ALLERGY', 'EXPLICITLY_ABSENT', 'Aucune', 'UNKNOWN', now())
                    """.formatted(practiceA, recordA))).hasMessageContaining("ck_clinical_assertion_source");
            c.rollback();
            scope(c, practiceA);
            assertThatThrownBy(() -> st.execute("""
                    INSERT INTO clinical_assertion (practice_id, record_id, lineage_id, revision, category, assertion_kind, event_precision, source_revision_id, recorded_at)
                    VALUES ('%s', '%s', gen_random_uuid(), 1, 'ALLERGY', 'PRESENT', 'UNKNOWN', '%s', now())
                    """.formatted(practiceA, otherRecord, first))).hasMessageContaining("fk_clinical_assertion_source");
            c.rollback();
        }
    }
}
