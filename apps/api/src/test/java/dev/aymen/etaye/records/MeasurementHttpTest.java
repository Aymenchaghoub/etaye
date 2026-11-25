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
 * The measurement reads of story 3.3 on records created inside the test stack: the cases the synthetic fixture cannot
 * hold (mixed units, an incomplete group, a series longer than a page, a member with CLINICAL_READ alone), the
 * database guarantees (read-only runtime role, immutability, constraints, row-level security) and the isolation and
 * denial behavior of the two operations (AT-011-A and AT-011-B, C1, C4 to C6, C9, C15).
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MeasurementHttpTest extends AbstractPostgresTest {

    private static final String V1_AT = "2026-10-20T10:13:00+02:00";
    private static final String V2_AT = "2026-10-20T10:15:00+02:00";

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

    @BeforeEach
    void fixtures() throws SQLException {
        practiceA = createPracticeAsOwner("Cabinet A " + UUID.randomUUID());
        practiceB = createPracticeAsOwner("Cabinet B " + UUID.randomUUID());
        UUID clinicalAAccount = createAccountAsOwner("a." + UUID.randomUUID() + "@example.test", "Dr A");
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
        recordA = createRecord(practiceA, "Alice", "Mesure");
        recordB = createRecord(practiceB, "Bruno", "Ailleurs");
    }

    // ---------------------------------------------------------------- setup helpers (owner connection)

    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
    }

    private static void scope(Connection c, UUID practice) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT set_config('etaye.practice_id', ?, true)")) {
            ps.setString(1, practice.toString());
            ps.execute();
        }
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

    /** One source revision of kind PRACTITIONER_MEASUREMENT, observed at its recording instant. */
    private UUID source(UUID practice, UUID record, String reference, String recordedAt) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection c = owner()) {
            c.setAutoCommit(false);
            scope(c, practice);
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO source_revision (id, practice_id, record_id, lineage_id, revision, reference, kind, label,
                        excerpt, source_type, event_precision, event_at, event_timezone, recorded_at, author_label)
                    VALUES (?, ?, ?, ?, 1, ?, 'PRACTITIONER_MEASUREMENT', 'Mesure', 'Texte', 'PRACTITIONER_OBSERVED', 'INSTANT',
                        CAST(? AS timestamptz), 'Europe/Paris', CAST(? AS timestamptz), 'Dr Test')
                    """)) {
                ps.setObject(1, id);
                ps.setObject(2, practice);
                ps.setObject(3, record);
                ps.setObject(4, UUID.randomUUID());
                ps.setString(5, reference);
                ps.setString(6, recordedAt);
                ps.setString(7, recordedAt);
                ps.executeUpdate();
            }
            c.commit();
        }
        return id;
    }

    private record Row(UUID practice, UUID record, UUID lineage, int revision, String concept, String value, String unit,
                       String observed, String recorded, UUID source, UUID group, UUID supersedes, String reason,
                       String author) {
        static Row original(UUID practice, UUID record, String concept, String value, String unit, String observed,
                            String recorded, UUID source) {
            return new Row(practice, record, UUID.randomUUID(), 1, concept, value, unit, observed, recorded, source, null,
                    null, null, "Dr Test");
        }

        Row inGroup(UUID group) {
            return new Row(practice, record, lineage, revision, concept, value, unit, observed, recorded, source, group,
                    supersedes, reason, author);
        }
    }

    private UUID measurement(Row row) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection c = owner()) {
            c.setAutoCommit(false);
            scope(c, row.practice());
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO measurement (id, practice_id, record_id, lineage_id, revision, concept, value_decimal, unit,
                        observed_at, recorded_at, author_label, source_revision_id, group_id, supersedes_id, correction_reason)
                    VALUES (?, ?, ?, ?, ?, ?, CAST(? AS numeric), ?, CAST(? AS timestamptz), CAST(? AS timestamptz), ?, ?, ?, ?, ?)
                    """)) {
                ps.setObject(1, id);
                ps.setObject(2, row.practice());
                ps.setObject(3, row.record());
                ps.setObject(4, row.lineage());
                ps.setInt(5, row.revision());
                ps.setString(6, row.concept());
                ps.setString(7, row.value());
                ps.setString(8, row.unit());
                ps.setString(9, row.observed());
                ps.setString(10, row.recorded());
                ps.setString(11, row.author());
                ps.setObject(12, row.source());
                ps.setObject(13, row.group());
                ps.setObject(14, row.supersedes());
                ps.setString(15, row.reason());
                ps.executeUpdate();
            }
            c.commit();
        }
        return id;
    }

    /** The weight of the fixture: 86.4 kg entered at 10:13, corrected to 68.40 kg at 10:15 (the trailing zero is on purpose). */
    private UUID[] weightLineage(UUID practice, UUID record) throws SQLException {
        UUID s1 = source(practice, record, "SRC-W-1", V1_AT);
        UUID s2 = source(practice, record, "SRC-W-2", V2_AT);
        Row first = Row.original(practice, record, "WEIGHT", "86.4", "kg", "2026-10-20T10:12:00+02:00", V1_AT, s1);
        UUID v1 = measurement(first);
        UUID v2 = measurement(new Row(practice, record, first.lineage(), 2, "WEIGHT", "68.40", "kg",
                "2026-10-20T10:12:00+02:00", V2_AT, s2, null, v1, "Correction d’une erreur de saisie", "Dr Test"));
        return new UUID[]{v1, v2};
    }

    // ---------------------------------------------------------------- HTTP helpers

    private ResponseEntity<Map<String, Object>> get(String cookie, String path) {
        var call = RestClient.create().get().uri(java.net.URI.create("http://localhost:" + port + "/api/v1" + path));
        if (cookie != null) call.header(HttpHeaders.COOKIE, cookie);
        return call.retrieve().onStatus(status -> true, (request, response) -> {})
                .toEntity(new ParameterizedTypeReference<>() {});
    }

    private static List<Map<String, Object>> items(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return (List<Map<String, Object>>) response.getBody().get("items");
    }

    private static List<String> values(ResponseEntity<Map<String, Object>> response) {
        return items(response).stream().map(item -> item.get("value") + " " + item.get("unit")).toList();
    }

    private static Map<String, Object> withoutCorrelation(Map<String, Object> problem) {
        var copy = new HashMap<>(problem);
        copy.remove("correlationId");
        return copy;
    }

    private String list(UUID practice, UUID record) {
        return "/practices/" + practice + "/records/" + record + "/measurements";
    }

    private String lineage(UUID practice, UUID measurement) {
        return "/practices/" + practice + "/measurements/" + measurement + "/revisions";
    }

    // ---------------------------------------------------------------- AT-011-A: the current revision, as stored

    @Test
    @DisplayName("AT-011-A, C2, C4: the current revision of every lineage, newest observation first, value and unit exactly as stored")
    void listReturnsCurrentRevisionsWithTheirProvenance() throws SQLException {
        UUID[] weight = weightLineage(practiceA, recordA);
        UUID group = UUID.randomUUID();
        UUID bp = source(practiceA, recordA, "SRC-BP", "2026-10-20T10:12:00+02:00");
        measurement(Row.original(practiceA, recordA, "SYSTOLIC_BLOOD_PRESSURE", "134", "mmHg", "2026-10-20T10:10:00+02:00",
                "2026-10-20T10:12:00+02:00", bp).inGroup(group));
        measurement(Row.original(practiceA, recordA, "DIASTOLIC_BLOOD_PRESSURE", "82", "mmHg", "2026-10-20T10:10:00+02:00",
                "2026-10-20T10:12:00+02:00", bp).inGroup(group));

        var response = get(clinicalA, list(practiceA, recordA));
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getETag()).isNull();
        assertThat(response.getBody()).containsEntry("nextCursor", null).doesNotContainKey("total");
        var items = items(response);
        // The weight is one item, its current revision; the original is not listed (it is in the lineage).
        assertThat(items).hasSize(3);
        var current = items.getFirst();
        assertThat(current.get("id")).isEqualTo(weight[1].toString());
        assertThat(current).containsEntry("concept", "WEIGHT").containsEntry("value", "68.40").containsEntry("unit", "kg")
                .containsEntry("revision", 2).containsEntry("version", 2).containsEntry("sourceType", "PRACTITIONER_OBSERVED")
                .containsEntry("sourceReference", "SRC-W-2").containsEntry("authorLabel", "Dr Test")
                .containsEntry("correctionReason", "Correction d’une erreur de saisie")
                .containsEntry("supersedesId", weight[0].toString());
        assertThat(current.get("observedAt").toString()).startsWith("2026-10-20T08:12");
        assertThat(current.get("recordedAt").toString()).startsWith("2026-10-20T08:15");
        assertThat(current).doesNotContainKey("groupId");
        // A blood-pressure reading is two components of one group, returned as stored.
        var components = items.subList(1, 3);
        assertThat(components).extracting(item -> item.get("groupId")).containsOnly(group.toString());
        assertThat(components).extracting(item -> item.get("concept"))
                .containsExactlyInAnyOrder("SYSTOLIC_BLOOD_PRESSURE", "DIASTOLIC_BLOOD_PRESSURE");
        assertThat(components).extracting(item -> item.get("value")).containsExactlyInAnyOrder("134", "82");
        assertThat(components).allSatisfy(item -> assertThat(item).doesNotContainKeys("correctionReason", "supersedesId"));
    }

    @Test
    @DisplayName("C4: the lineage lists every revision oldest first with its reason, and an original has none")
    void lineageListsRevisionsOldestFirst() throws SQLException {
        UUID[] weight = weightLineage(practiceA, recordA);
        // Any revision of the lineage resolves the same lineage.
        for (UUID anchor : weight) {
            var response = get(clinicalA, lineage(practiceA, anchor));
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
            var revisions = items(response);
            assertThat(revisions).extracting(item -> item.get("revision")).containsExactly(1, 2);
            assertThat(values(response)).containsExactly("86.4 kg", "68.40 kg");
            assertThat(revisions.getFirst()).doesNotContainKeys("correctionReason", "supersedesId");
            assertThat(revisions.getLast()).containsEntry("correctionReason", "Correction d’une erreur de saisie");
            assertThat(revisions.getFirst().get("sourceReference")).isEqualTo("SRC-W-1");
            assertThat(revisions.getFirst().get("recordedAt").toString()).startsWith("2026-10-20T08:13");
        }
    }

    // ---------------------------------------------------------------- UD-32: the as-of view

    @Test
    @DisplayName("AT-011-A, C5: the as-of view shows the weight before and after the correction and never a later revision")
    void asOfShowsTheWeightBeforeAndAfterTheCorrection() throws SQLException {
        UUID[] weight = weightLineage(practiceA, recordA);

        var before = get(clinicalA, list(practiceA, recordA) + "?asOf=2026-10-20T08:14:00Z");
        assertThat(values(before)).containsExactly("86.4 kg");
        assertThat(items(before).getFirst()).containsEntry("revision", 1).doesNotContainKeys("correctionReason", "supersedesId");
        var after = get(clinicalA, list(practiceA, recordA) + "?asOf=2026-10-20T08:16:00Z");
        assertThat(values(after)).containsExactly("68.40 kg");
        assertThat(get(clinicalA, list(practiceA, recordA)).getBody().get("items").toString()).contains("68.40");

        // 100 ns before the correction the old value is still the known one (the driver rounds nanoseconds up).
        assertThat(values(get(clinicalA, list(practiceA, recordA) + "?asOf=2026-10-20T08:14:59.9999999Z"))).containsExactly("86.4 kg");
        assertThat(values(get(clinicalA, list(practiceA, recordA) + "?asOf=2026-10-20T08:15:00Z"))).containsExactly("68.40 kg");

        // Before the first recording nothing is known: an empty list, never a value.
        var nothing = get(clinicalA, list(practiceA, recordA) + "?asOf=2026-10-20T08:00:00Z");
        assertThat(items(nothing)).isEmpty();

        // The lineage at an earlier instant lists only what was recorded then, and a later revision is a 404.
        var earlyLineage = get(clinicalA, lineage(practiceA, weight[0]) + "?asOf=2026-10-20T08:14:00Z");
        assertThat(values(earlyLineage)).containsExactly("86.4 kg");
        var hidden = get(clinicalA, lineage(practiceA, weight[1]) + "?asOf=2026-10-20T08:14:00Z");
        var unknown = get(clinicalA, lineage(practiceA, UUID.randomUUID()) + "?asOf=2026-10-20T08:14:00Z");
        assertThat(hidden.getStatusCode().value()).isEqualTo(404);
        assertThat(withoutCorrelation(hidden.getBody())).isEqualTo(withoutCorrelation(unknown.getBody()));

        // A future or malformed instant is refused, never clamped.
        assertThat(get(clinicalA, list(practiceA, recordA) + "?asOf=2999-01-01T00:00:00Z").getStatusCode().value()).isEqualTo(400);
        assertThat(get(clinicalA, list(practiceA, recordA) + "?asOf=hier").getStatusCode().value()).isEqualTo(400);
        assertThat(get(clinicalA, list(practiceA, recordA) + "?asOf=2026-10-20T10:14:00").getStatusCode().value()).isEqualTo(400);
        assertThat(get(clinicalA, lineage(practiceA, weight[0]) + "?asOf=2999-01-01T00:00:00Z").getStatusCode().value()).isEqualTo(400);
    }

    // ---------------------------------------------------------------- AT-011-B, C6, C7: no silent merge

    @Test
    @DisplayName("AT-011-B, C6: two units or two spellings are two series, never converted or merged, and the concept filter is exact")
    void unitsAreNeverMergedOrConverted() throws SQLException {
        UUID s = source(practiceA, recordA, "SRC-U", "2026-10-01T10:00:00+02:00");
        measurement(Row.original(practiceA, recordA, "WEIGHT", "70", "kg", "2026-09-01T09:00:00+02:00", "2026-10-01T10:00:00+02:00", s));
        measurement(Row.original(practiceA, recordA, "WEIGHT", "154.3", "lb", "2026-09-02T09:00:00+02:00", "2026-10-01T10:00:00+02:00", s));
        measurement(Row.original(practiceA, recordA, "WEIGHT", "70", "kilogramme", "2026-09-03T09:00:00+02:00", "2026-10-01T10:00:00+02:00", s));
        measurement(Row.original(practiceA, recordA, "weight", "70", "kg", "2026-09-04T09:00:00+02:00", "2026-10-01T10:00:00+02:00", s));

        // Every row comes back with the unit it was entered with: the server converts and normalizes nothing.
        assertThat(values(get(clinicalA, list(practiceA, recordA)))).containsExactly("70 kg", "70 kilogramme", "154.3 lb", "70 kg");
        // The concept is compared as stored: case-sensitive, exact, repeatable.
        assertThat(items(get(clinicalA, list(practiceA, recordA) + "?concept=WEIGHT"))).hasSize(3);
        assertThat(items(get(clinicalA, list(practiceA, recordA) + "?concept=weight"))).hasSize(1);
        assertThat(items(get(clinicalA, list(practiceA, recordA) + "?concept=WEIGHT&concept=weight"))).hasSize(4);
        assertThat(items(get(clinicalA, list(practiceA, recordA) + "?concept=POIDS"))).isEmpty();
        // Invalid filters are refused.
        assertThat(get(clinicalA, list(practiceA, recordA) + "?concept=").getStatusCode().value()).isEqualTo(400);
        assertThat(get(clinicalA, list(practiceA, recordA) + "?concept=" + "X".repeat(101)).getStatusCode().value()).isEqualTo(400);
        String eleven = String.join("&", java.util.stream.IntStream.range(0, 11).mapToObj(i -> "concept=C" + i).toList());
        assertThat(get(clinicalA, list(practiceA, recordA) + "?" + eleven).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("C7: an incomplete blood-pressure group is returned as it is, never completed")
    void anIncompleteGroupIsReturnedAsIs() throws SQLException {
        UUID group = UUID.randomUUID();
        UUID s = source(practiceA, recordA, "SRC-I", "2026-11-12T08:41:00+01:00");
        measurement(Row.original(practiceA, recordA, "SYSTOLIC_BLOOD_PRESSURE", "130", "mmHg", "2026-11-12T08:40:00+01:00",
                "2026-11-12T08:41:00+01:00", s).inGroup(group));
        var items = items(get(clinicalA, list(practiceA, recordA)));
        assertThat(items).hasSize(1);
        assertThat(items.getFirst()).containsEntry("concept", "SYSTOLIC_BLOOD_PRESSURE").containsEntry("groupId", group.toString());
    }

    @Test
    @DisplayName("C9, C10: 25 by default and 100 at most, a bound cursor, bounds on the observed time and no total")
    void pagingAndBounds() throws SQLException {
        UUID s = source(practiceA, recordA, "SRC-P", "2026-10-01T10:00:00+02:00");
        for (int i = 0; i < 30; i++) {
            measurement(Row.original(practiceA, recordA, "PULSE", String.valueOf(60 + i), "bpm",
                    "2026-09-%02dT09:00:00+02:00".formatted(i % 28 + 1), "2026-10-01T10:00:00+02:00", s));
        }
        var first = get(clinicalA, list(practiceA, recordA));
        assertThat(items(first)).hasSize(25);
        String cursor = (String) first.getBody().get("nextCursor");
        assertThat(cursor).isNotBlank();
        assertThat(first.getBody()).doesNotContainKey("total");
        var second = get(clinicalA, list(practiceA, recordA) + "?cursor=" + cursor);
        assertThat(items(second)).hasSize(5);
        assertThat(second.getBody()).containsEntry("nextCursor", null);
        // The pages do not overlap and cover all 30 rows.
        var ids = new ArrayList<>(items(first).stream().map(item -> item.get("id")).toList());
        ids.addAll(items(second).stream().map(item -> item.get("id")).toList());
        assertThat(ids).doesNotHaveDuplicates().hasSize(30);

        assertThat(items(get(clinicalA, list(practiceA, recordA) + "?limit=100"))).hasSize(30);
        assertThat(get(clinicalA, list(practiceA, recordA) + "?limit=0").getStatusCode().value()).isEqualTo(400);
        assertThat(get(clinicalA, list(practiceA, recordA) + "?limit=101").getStatusCode().value()).isEqualTo(400);
        // A cursor belongs to its filters, its record and its actor, and a tampered one fails safely.
        assertThat(get(clinicalA, list(practiceA, recordA) + "?concept=PULSE&cursor=" + cursor).getStatusCode().value()).isEqualTo(400);
        assertThat(get(clinicalA, list(practiceA, recordA) + "?asOf=2026-10-15T00:00:00Z&cursor=" + cursor).getStatusCode().value()).isEqualTo(400);
        assertThat(get(readOnlyA, list(practiceA, recordA) + "?cursor=" + cursor).getStatusCode().value()).isEqualTo(400);
        assertThat(get(clinicalA, list(practiceA, recordA) + "?cursor=" + cursor.substring(0, cursor.length() - 4) + "AAAA").getStatusCode().value()).isEqualTo(400);
        assertThat(get(clinicalA, list(practiceA, recordA) + "?cursor=pas-un-curseur").getStatusCode().value()).isEqualTo(400);

        // from is inclusive and to is exclusive on the observed time; reversed bounds are refused.
        assertThat(items(get(clinicalA, list(practiceA, recordA) + "?from=2026-09-05T07:00:00Z&to=2026-09-07T07:00:00Z"))).hasSize(2);
        assertThat(items(get(clinicalA, list(practiceA, recordA) + "?from=2026-09-05T07:00:00Z&to=2026-09-05T07:00:00Z"))).isEmpty();
        assertThat(get(clinicalA, list(practiceA, recordA) + "?from=2026-09-07T07:00:00Z&to=2026-09-05T07:00:00Z").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("C12: a decimal of 12 integer and 6 fractional digits and long untrusted text come back as written")
    void longValuesAndUntrustedTextComeBackAsWritten() throws SQLException {
        UUID s = source(practiceA, recordA, "SRC-LONG", "2026-10-01T10:00:00+02:00");
        String concept = "<img src=x onerror=alert(1)> " + "C".repeat(80);
        String unit = "u".repeat(40);
        measurement(new Row(practiceA, recordA, UUID.randomUUID(), 1, concept.substring(0, 100), "123456789012.123456", unit,
                "2026-09-01T09:00:00+02:00", "2026-10-01T10:00:00+02:00", s, null, null, null, "A".repeat(200)));
        var item = items(get(readOnlyA, list(practiceA, recordA))).getFirst();
        assertThat(item).containsEntry("value", "123456789012.123456").containsEntry("unit", unit);
        assertThat(item.get("concept").toString()).hasSize(100).startsWith("<img src=x");
        assertThat(item.get("authorLabel").toString()).hasSize(200);
    }

    // ---------------------------------------------------------------- C1: isolation and denials

    @Test
    @DisplayName("C1: another practice's record and measurement are 404 like an absent one, denials are audited, reused connections never leak")
    void isolationDenialsAndConnectionReuse() throws SQLException {
        UUID[] weightA = weightLineage(practiceA, recordA);
        UUID sourceB = source(practiceB, recordB, "SRC-B", "2026-10-02T10:00:00+02:00");
        UUID foreignMeasurement = measurement(Row.original(practiceB, recordB, "WEIGHT", "90", "kg", "2026-10-01T09:00:00+02:00",
                "2026-10-02T10:00:00+02:00", sourceB));

        var foreign = get(clinicalA, list(practiceA, recordB));
        var absent = get(clinicalA, list(practiceA, UUID.randomUUID()));
        assertThat(foreign.getStatusCode().value()).isEqualTo(404);
        assertThat(withoutCorrelation(foreign.getBody())).isEqualTo(withoutCorrelation(absent.getBody()));
        // The route practice never grants authority: B's record through B's route needs B's membership.
        assertThat(get(clinicalA, list(practiceB, recordB)).getStatusCode().value()).isEqualTo(404);
        // A guessed foreign measurement on the lineage route is the same 404 as an unknown identifier.
        var guessed = get(clinicalA, lineage(practiceA, foreignMeasurement));
        var unknown = get(clinicalA, lineage(practiceA, UUID.randomUUID()));
        assertThat(guessed.getStatusCode().value()).isEqualTo(404);
        assertThat(withoutCorrelation(guessed.getBody())).isEqualTo(withoutCorrelation(unknown.getBody()));
        assertThat(get(clinicalB, lineage(practiceB, weightA[1])).getStatusCode().value()).isEqualTo(404);
        assertThat(get(clinicalB, lineage(practiceB, foreignMeasurement)).getStatusCode().value()).isEqualTo(200);

        // 403 for the administrator without a clinical capability, and each denial writes exactly one security event.
        var forbidden = get(adminOnlyA, list(practiceA, recordA));
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(deniedEvents(forbidden.getBody().get("correlationId").toString())).isEqualTo(1);
        assertThat(get(adminOnlyA, lineage(practiceA, weightA[0])).getStatusCode().value()).isEqualTo(403);
        // The denial audit is bounded per actor, so the hidden-practice case uses an actor with no earlier denial.
        var hidden = get(readOnlyA, list(practiceB, recordB));
        assertThat(hidden.getStatusCode().value()).isEqualTo(404);
        assertThat(deniedEvents(hidden.getBody().get("correlationId").toString())).isEqualTo(1);
        // Without a session nothing is read.
        assertThat(get(null, list(practiceA, recordA)).getStatusCode().value()).isEqualTo(401);

        // Pooled connections are reused across practices: each actor only ever sees its own measurements.
        for (int round = 0; round < 12; round++) {
            assertThat(values(get(clinicalA, list(practiceA, recordA)))).containsExactly("68.40 kg");
            assertThat(values(get(clinicalB, list(practiceB, recordB)))).containsExactly("90 kg");
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
    @DisplayName("C15: reads change nothing, and the runtime role cannot write, update or delete measurements")
    void readsAreSideEffectFreeAndTheRuntimeRoleIsReadOnly() throws SQLException {
        UUID[] weight = weightLineage(practiceA, recordA);
        String before = recordState();
        List<String> correlations = new ArrayList<>();
        for (String path : List.of(list(practiceA, recordA), lineage(practiceA, weight[1]))) {
            var response = get(clinicalA, path);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            correlations.add(response.getHeaders().getFirst("X-Correlation-Id"));
        }
        assertThat(recordState()).isEqualTo(before);
        // D-62: a routine read writes no audit row of any type under its correlation identifier.
        assertThat(correlations).doesNotContainNull();
        for (String correlation : correlations) {
            assertThat(auditRowsFor(correlation)).as(correlation).isZero();
        }

        try (Connection runtime = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), RUNTIME_USER, RUNTIME_PASSWORD);
             Statement st = runtime.createStatement()) {
            st.execute("SELECT set_config('etaye.practice_id', '" + practiceA + "', false)");
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM measurement")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
            for (String sql : List.of(
                    "INSERT INTO measurement (practice_id, record_id, lineage_id, revision, concept, value_decimal, unit, observed_at, recorded_at, author_label, source_revision_id) "
                            + "VALUES ('" + practiceA + "', '" + recordA + "', gen_random_uuid(), 1, 'X', 1, 'u', now(), now(), 'x', gen_random_uuid())",
                    "UPDATE measurement SET unit = 'altéré'",
                    "DELETE FROM measurement",
                    "TRUNCATE measurement")) {
                assertThatThrownBy(() -> st.execute(sql)).as(sql).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            }
            // Row-level security: another practice's scope sees none of it, and neither does no scope at all.
            st.execute("SELECT set_config('etaye.practice_id', '" + practiceB + "', false)");
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM measurement")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
            st.execute("SELECT set_config('etaye.practice_id', '', false)");
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM measurement")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
        // The identity role has no access to measurements either.
        try (Connection identity = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), IDENTITY_USER, IDENTITY_PASSWORD);
             Statement st = identity.createStatement()) {
            assertThatThrownBy(() -> st.execute("SELECT * FROM measurement")).hasMessageContaining("permission denied");
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

    private int auditRowsFor(String correlationId) throws SQLException {
        try (Connection c = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword());
             PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM audit_event WHERE correlation_id = ?")) {
            ps.setString(1, correlationId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // ---------------------------------------------------------------- database integrity

    @Test
    @DisplayName("Measurement integrity: immutable rows, a reason for a correction, evidence recorded first, one lineage, bounded decimals")
    void databaseConstraintsProtectTheMeasurements() throws SQLException {
        UUID early = source(practiceA, recordA, "SRC-E", "2026-10-01T10:00:00+02:00");
        UUID late = source(practiceA, recordA, "SRC-L", "2026-10-03T10:00:00+02:00");
        UUID foreignSource = source(practiceB, recordB, "SRC-F", "2026-10-01T10:00:00+02:00");
        Row base = Row.original(practiceA, recordA, "WEIGHT", "70", "kg", "2026-09-30T09:00:00+02:00", "2026-10-02T10:00:00+02:00", early);
        UUID original = measurement(base);

        // A revision is superseded, never edited or removed (the immutability guard).
        try (Connection c = owner(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            scope(c, practiceA);
            assertThatThrownBy(() -> st.execute("UPDATE measurement SET unit = 'g' WHERE id = '" + original + "'"))
                    .hasMessageContaining("immutable");
            c.rollback();
        }
        try (Connection c = owner(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            scope(c, practiceA);
            assertThatThrownBy(() -> st.execute("DELETE FROM measurement WHERE id = '" + original + "'")).hasMessageContaining("immutable");
            c.rollback();
        }

        // A correction needs its reason, an original has none, and a correction follows its predecessor.
        Row correction = new Row(practiceA, recordA, base.lineage(), 2, "WEIGHT", "71", "kg", "2026-09-30T09:00:00+02:00",
                "2026-10-03T10:00:00+02:00", late, null, original, "Erreur", "Dr Test");
        assertThatThrownBy(() -> measurement(new Row(correction.practice(), correction.record(), correction.lineage(), 2, "WEIGHT", "71", "kg",
                correction.observed(), correction.recorded(), late, null, original, null, "Dr Test"))).hasMessageContaining("ck_measurement_correction");
        assertThatThrownBy(() -> measurement(new Row(practiceA, recordA, UUID.randomUUID(), 1, "WEIGHT", "71", "kg",
                correction.observed(), correction.recorded(), late, null, null, "Raison sur un original", "Dr Test")))
                .hasMessageContaining("ck_measurement_correction");
        assertThatThrownBy(() -> measurement(new Row(practiceA, recordA, base.lineage(), 3, "WEIGHT", "71", "kg", correction.observed(),
                correction.recorded(), late, null, original, "Saut de révision", "Dr Test"))).hasMessageContaining("ck_measurement_order");
        // The correction keeps the concept and the group of its lineage.
        assertThatThrownBy(() -> measurement(new Row(practiceA, recordA, base.lineage(), 2, "HEIGHT", "71", "kg", correction.observed(),
                correction.recorded(), late, null, original, "Autre concept", "Dr Test"))).hasMessageContaining("ck_measurement_order");
        assertThatThrownBy(() -> measurement(correction.inGroup(UUID.randomUUID()))).hasMessageContaining("ck_measurement_order");
        // A correction recorded before the revision it supersedes is refused.
        assertThatThrownBy(() -> measurement(new Row(practiceA, recordA, base.lineage(), 2, "WEIGHT", "71", "kg", correction.observed(),
                "2026-10-01T12:00:00+02:00", early, null, original, "Trop tôt", "Dr Test"))).hasMessageContaining("ck_measurement_order");
        measurement(correction);

        // A measurement is never recorded before the source revision it cites.
        assertThatThrownBy(() -> measurement(Row.original(practiceA, recordA, "WEIGHT", "70", "kg", "2026-09-30T09:00:00+02:00",
                "2026-10-02T10:00:00+02:00", late))).hasMessageContaining("ck_measurement_order");
        // A source of another practice cannot be cited, and a record of another practice cannot be targeted.
        assertThatThrownBy(() -> measurement(Row.original(practiceA, recordA, "WEIGHT", "70", "kg", "2026-09-30T09:00:00+02:00",
                "2026-10-02T10:00:00+02:00", foreignSource))).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> measurement(Row.original(practiceA, recordB, "WEIGHT", "70", "kg", "2026-09-30T09:00:00+02:00",
                "2026-10-02T10:00:00+02:00", early))).hasMessageContaining("violates foreign key constraint");

        // The decimal keeps its scale up to 6 fractional digits and 12 integer digits; blank text is refused.
        assertThatThrownBy(() -> measurement(Row.original(practiceA, recordA, "W", "1.1234567", "kg", "2026-09-30T09:00:00+02:00",
                "2026-10-02T10:00:00+02:00", early))).hasMessageContaining("ck_measurement_value");
        assertThatThrownBy(() -> measurement(Row.original(practiceA, recordA, "W", "1000000000000", "kg", "2026-09-30T09:00:00+02:00",
                "2026-10-02T10:00:00+02:00", early))).hasMessageContaining("ck_measurement_value");
        assertThatThrownBy(() -> measurement(Row.original(practiceA, recordA, "W", "1", " ", "2026-09-30T09:00:00+02:00",
                "2026-10-02T10:00:00+02:00", early))).hasMessageContaining("ck_measurement_text");
        measurement(Row.original(practiceA, recordA, "W", "-0.000001", "kg", "2026-09-30T09:00:00+02:00", "2026-10-02T10:00:00+02:00", early));
    }

    // ---------------------------------------------------------------- D-64: the practice time zone

    @Test
    @DisplayName("D-64: the membership context carries the IANA time zone of its practice")
    void theSessionContextCarriesThePracticeTimeZone() throws SQLException {
        var before = get(clinicalA, "/session");
        assertThat(contexts(before)).singleElement().satisfies(context -> {
            assertThat(context).containsEntry("kind", "MEMBERSHIP").containsEntry("practiceId", practiceA.toString());
            assertThat(context.get("timezone")).isEqualTo("Europe/Paris");
        });
        try (Connection c = owner(); PreparedStatement ps = c.prepareStatement("UPDATE practice SET timezone = ? WHERE id = ?")) {
            ps.setString(1, "America/Martinique");
            ps.setObject(2, practiceA);
            ps.executeUpdate();
        }
        assertThat(contexts(get(clinicalA, "/session"))).singleElement()
                .satisfies(context -> assertThat(context.get("timezone")).isEqualTo("America/Martinique"));
        // Another practice's zone never reaches this actor.
        assertThat(contexts(get(clinicalB, "/session"))).singleElement()
                .satisfies(context -> assertThat(context.get("timezone")).isEqualTo("Europe/Paris"));
    }

    private static List<Map<String, Object>> contexts(ResponseEntity<Map<String, Object>> session) {
        assertThat(session.getStatusCode().value()).isEqualTo(200);
        return (List<Map<String, Object>>) session.getBody().get("contexts");
    }
}
