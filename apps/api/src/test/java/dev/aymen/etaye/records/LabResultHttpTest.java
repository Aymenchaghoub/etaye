package dev.aymen.etaye.records;

import dev.aymen.etaye.infrastructure.demo.DemoDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "etaye.demo.enabled=true")
@ActiveProfiles("test")
class LabResultHttpTest {

    private static final String PASSWORD = "Etaye-Jeu-Fictif-91!";

    @DynamicPropertySource
    static void demoDatabase(DynamicPropertyRegistry registry) {
        DemoDatabase.configureDemo(registry);
    }

    @LocalServerPort private int port;

    private static String practiceA;
    private static String practiceB;
    private static String claire;
    private static String louis;
    private static String hugo;
    private static String marie;

    @BeforeAll
    static void identifiers() throws SQLException {
        practiceA = value("SELECT etaye_demo.fixture_id('PRA-A')");
        practiceB = value("SELECT etaye_demo.fixture_id('PRA-B')");
        claire = value("SELECT etaye_demo.fixture_id('R-A-001')");
        louis = value("SELECT etaye_demo.fixture_id('R-A-002')");
        hugo = value("SELECT etaye_demo.fixture_id('R-B-001')");
        marie = value("SELECT etaye_demo.fixture_id('R-A-003')");
    }

    private static String value(String sql) throws SQLException {
        try (var connection = DemoDatabase.ownerConnection(); var command = connection.createStatement();
             var result = command.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    /** One sign-in per account for the whole class: the sign-in budget is bounded and a session outlives the suite. */
    private static final Map<String, String> COOKIES = new java.util.concurrent.ConcurrentHashMap<>();

    private String login(String email) {
        return COOKIES.computeIfAbsent(email, this::signIn);
    }

    private String signIn(String email) {
        var csrf = RestClient.create().get().uri(URI.create("http://localhost:" + port + "/api/v1/csrf"))
                .retrieve().toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
        String cookie = csrf.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
        var response = RestClient.create().post().uri(URI.create("http://localhost:" + port + "/api/v1/session"))
                .header(HttpHeaders.COOKIE, cookie).header("X-CSRF-TOKEN", csrf.getBody().get("token").toString())
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("email", email, "password", PASSWORD))
                .retrieve().toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
    }

    private ResponseEntity<Map<String, Object>> get(String cookie, String path) {
        var call = RestClient.create().method(HttpMethod.GET).uri(URI.create("http://localhost:" + port + "/api/v1" + path));
        if (cookie != null) call.header(HttpHeaders.COOKIE, cookie);
        return call.retrieve().onStatus(status -> true, (request, response) -> {})
                .toEntity(new ParameterizedTypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return (List<Map<String, Object>>) response.getBody().get("items");
    }

    private static Map<String, Object> withoutCorrelation(Map<String, Object> problem) {
        var copy = new java.util.HashMap<>(problem);
        copy.remove("correlationId");
        return copy;
    }

    private String list(String record) {
        return "/practices/" + practiceA + "/records/" + record + "/lab-results";
    }

    @Test
    @DisplayName("AT-012-A: canonical ranges and source, exact decimal and comparator, qualitative text and supplied precision")
    void seedPreservesValueIntervalDateAndSource() {
        String nora = login("nora.lefebvre@example.test");
        var claireResult = get(nora, list(claire));
        assertThat(claireResult.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(claireResult.getHeaders().getETag()).isNull();
        var claireRows = items(claireResult);
        assertThat(claireRows).extracting(row -> row.get("numericValue")).containsExactlyInAnyOrder("104", "96", "5.2");
        var glucose = claireRows.stream().filter(row -> "Glucose".equals(row.get("analyte"))).findFirst().orElseThrow();
        assertThat(glucose).containsEntry("unit", "mmol/L").containsEntry("lowerBound", "4.1")
                .containsEntry("upperBound", "6.1").containsEntry("intervalUnit", "mmol/L");
        var source = get(nora, "/practices/" + practiceA + "/sources/" + glucose.get("sourceRevisionId"));
        assertThat(source.getBody()).containsEntry("locator", "DOC-A-001:page-1");
        assertThat(source.getBody().get("excerpt").toString()).contains("glucose 5,2 mmol/L", "4,1–6,1 mmol/L");

        var rows = items(get(nora, list(marie)));
        var comparator = rows.stream().filter(row -> "LT".equals(row.get("comparator"))).findFirst().orElseThrow();
        assertThat(comparator).containsEntry("numericValue", "0.060").containsEntry("unit", "unité B")
                .doesNotContainKeys("lowerBound", "upperBound", "referenceIntervalText", "specimenAt");
        assertThat(comparator.get("specimenDate")).isEqualTo(Map.of("precision", "DATE", "date", "2026-10-22", "timezone", "Europe/Paris"));
        var text = rows.getLast();
        assertThat(text).containsEntry("valueKind", "QUALITATIVE").containsEntry("qualitativeValue", "Non détecté dans le prélèvement")
                .doesNotContainKeys("numericValue", "comparator", "unit", "lowerBound", "upperBound");
        assertThat(text.get("eventDate")).isEqualTo(Map.of("precision", "UNKNOWN"));
        assertThat(items(get(nora, list(louis)))).isEmpty();
    }

    @Test
    @DisplayName("AT-012-B: the asOf cutoff and correct route hide later and wrong-record revisions")
    void lineageHonorsTheViewAndRecord() {
        String nora = login("nora.lefebvre@example.test");
        String base = list(marie);
        String before = "?asOf=2026-10-21T08:14:59.9999999Z";
        String after = "?asOf=2026-10-21T08:15:00Z";
        var early = items(get(nora, base + before));
        assertThat(early).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("numericValue", "7.40").doesNotContainKeys("supersedesId", "correctionReason");
        });
        var revised = items(get(nora, base + after)).getFirst();
        assertThat(revised).containsEntry("numericValue", "7.04").containsEntry("revision", 2);
        String originalPath = base + "/" + early.getFirst().get("id") + "/revisions";
        String changedPath = base + "/" + revised.get("id") + "/revisions";
        var visible = get(nora, changedPath + after);
        assertThat(visible.getBody().get("revisions")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(Map.class))
                .extracting(row -> row.get("numericValue")).containsExactly("7.40", "7.04");
        assertThat(get(nora, originalPath + before).getBody().get("revisions"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(Map.class)).hasSize(1);
        var absent = get(nora, base + "/" + UUID.randomUUID() + "/revisions" + before);
        var later = get(nora, changedPath + before);
        assertThat(later.getStatusCode().value()).isEqualTo(404);
        assertThat(withoutCorrelation(later.getBody())).isEqualTo(withoutCorrelation(absent.getBody()));
        assertThat(get(nora, list(claire) + "/" + revised.get("id") + "/revisions").getStatusCode().value()).isEqualTo(404);
        assertThat(get(nora, "/practices/" + practiceA + "/sources/" + revised.get("sourceRevisionId") + before)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(get(nora, base + "?asOf=2999-01-01T00:00:00Z").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void cursorPinsTheViewAndRejectsAChangedRecordActorOrFilter() {
        String nora = login("nora.lefebvre@example.test");
        String sami = login("sami.rahmani@example.test");
        String base = list(claire);
        var initial = get(nora, base + "?limit=1");
        String cursor = initial.getBody().get("nextCursor").toString();
        var next = get(nora, base + "?limit=1&cursor=" + cursor);
        var tied = List.of(items(initial).getFirst(), items(next).getFirst());
        assertThat(tied).extracting(row -> row.get("numericValue")).containsExactlyInAnyOrder("104", "5.2");
        assertThat(tied).extracting(row -> row.get("eventDate")).containsOnly(tied.getFirst().get("eventDate"));
        assertThat(tied).extracting(row -> row.get("recordedAt")).containsOnly(tied.getFirst().get("recordedAt"));
        var last = get(nora, base + "?limit=1&cursor=" + next.getBody().get("nextCursor"));
        assertThat(items(last)).extracting(row -> row.get("numericValue")).containsExactly("96");
        assertThat(last.getBody()).containsEntry("nextCursor", null);
        assertThat(next.getBody().get("viewAt")).isEqualTo(initial.getBody().get("viewAt"));
        assertThat(get(nora, list(marie) + "?cursor=" + cursor).getStatusCode().value()).isEqualTo(400);
        assertThat(get(sami, base + "?cursor=" + cursor).getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "?from=2026-09-10T00:00:00Z&cursor=" + cursor).getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "?cursor=%%%".replace("%", "%25")).getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "?cursor=").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "?limit=0").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "?limit=101").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "?analyteKey=").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "?analyteKey=x&analyteKey=x").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "?from=2026-10-22T00:00:00Z&to=2026-10-21T00:00:00Z").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void exactAnalyteOptionsAreBoundedAndUnknownDatesAreExcludedByEitherBound() {
        String nora = login("nora.lefebvre@example.test");
        String base = list(marie);
        var options = get(nora, base + "/analytes?limit=1");
        var option = items(options).getFirst();
        assertThat(option).containsEntry("label", "Analyse A");
        String key = option.get("key").toString();
        assertThat(key).matches("[0-9a-f]{64}");
        String optionCursor = options.getBody().get("nextCursor").toString();
        assertThat(items(get(nora, base + "/analytes?cursor=" + optionCursor))).extracting(row -> row.get("label"))
                .doesNotContain("Analyse A").contains("Analyse B", "Analyse C");
        assertThat(get(nora, base + "?cursor=" + optionCursor).getStatusCode().value()).isEqualTo(400);
        assertThat(items(get(nora, base + "?analyteKey=" + key))).extracting(row -> row.get("numericValue")).containsExactly("7.04");
        assertThat(items(get(nora, list(claire) + "?analyteKey=" + key))).isEmpty();

        var from = items(get(nora, base + "?from=2026-10-21T22:00:00Z&to=2026-10-22T00:00:00Z"));
        assertThat(from).extracting(row -> row.get("numericValue")).containsExactly("0.060");
        var until = items(get(nora, base + "?to=2026-10-22T00:00:00Z"));
        assertThat(until).noneMatch(row -> "QUALITATIVE".equals(row.get("valueKind")));
    }

    @Test
    void aLongLineageKeepsTheOriginalAndTheNewestNinetyNine() throws SQLException {
        inCanalFixture("""
                DO $$
                DECLARE
                    previous uuid;
                    current uuid;
                    lineage uuid := gen_random_uuid();
                BEGIN
                    FOR position IN 1..105 LOOP
                        current := etaye_demo.fixture_id('test-lab-lineage-' || position);
                        INSERT INTO public.lab_result (id, practice_id, record_id, lineage_id, revision, analyte,
                            numeric_value, specimen_on, source_revision_id, author_label, recorded_at, supersedes_id, correction_reason)
                        VALUES (current, etaye_demo.fixture_id('PRA-B'), etaye_demo.fixture_id('R-B-001'), lineage,
                            position, 'Analyse fictive avec révisions', position, DATE '2026-10-29',
                            etaye_demo.fixture_id('SRC-B-001'), 'Dr Essai', '2026-10-29T10:00:00Z'::timestamptz + position * interval '1 second',
                            previous, CASE WHEN previous IS NOT NULL THEN repeat('r', 1000) END);
                        previous := current;
                    END LOOP;
                END $$
                """);
        String base = list(hugo).replace(practiceA, practiceB);
        String original = value("SELECT etaye_demo.fixture_id('test-lab-lineage-1')");
        String current = value("SELECT etaye_demo.fixture_id('test-lab-lineage-105')");
        String sami = login("sami.rahmani@example.test");
        var response = get(sami, base + "/" + current + "/revisions");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("truncated", true);
        assertThat(response.getBody().get("revisions"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(Map.class))
                .hasSize(100).satisfies(rows -> {
                    assertThat(rows.getFirst().get("id")).isEqualTo(original);
                    assertThat(rows.get(1).get("revision")).isEqualTo(7);
                    assertThat(rows.getLast().get("id")).isEqualTo(current);
                    assertThat(rows.getLast().get("correctionReason")).isEqualTo("r".repeat(1000));
                });
        assertThat(get(sami, base + "/" + original + "/revisions").getBody()).isEqualTo(response.getBody());
    }

    @Test
    void dateBoundariesUseTheCabinetZoneOnADaylightSavingChange() throws SQLException {
        inCanalFixture("""
                INSERT INTO public.lab_result (practice_id, record_id, lineage_id, revision, analyte,
                    numeric_value, specimen_on, source_revision_id, author_label, recorded_at)
                VALUES (etaye_demo.fixture_id('PRA-B'), etaye_demo.fixture_id('R-B-001'), gen_random_uuid(), 1,
                    'Analyse fictive à date seule', 1, DATE '2026-10-25', etaye_demo.fixture_id('SRC-B-001'),
                    'Dr Essai', '2026-10-29T10:00:00Z');
                """);
        String base = list(hugo).replace(practiceA, practiceB);
        String sami = login("sami.rahmani@example.test");
        // Paris still observes UTC+02 at midnight on the day clocks fall back to UTC+01.
        String midnight = "2026-10-24T22:00:00Z";
        assertThat(items(get(sami, base + "?to=" + midnight))).isEmpty();
        assertThat(items(get(sami, base + "?from=" + midnight + "&to=2026-10-24T22:01:00Z")))
                .singleElement().satisfies(row -> assertThat(row.get("specimenDate")).isEqualTo(
                        Map.of("precision", "DATE", "date", "2026-10-25", "timezone", "Europe/Paris")));
    }

    private static void inCanalFixture(String sql) throws SQLException {
        try (var connection = DemoDatabase.ownerConnection(); var command = connection.createStatement()) {
            connection.setAutoCommit(false);
            command.execute("SELECT set_config('etaye.practice_id', etaye_demo.fixture_id('PRA-B')::text, true)");
            command.execute(sql);
            connection.commit();
        }
    }

    @Test
    void storageRefusesInconsistentValuesBoundsSourcesAndPredecessors() throws SQLException {
        try (var connection = DemoDatabase.ownerConnection(); var command = connection.createStatement()) {
            connection.setAutoCommit(false);
            command.execute("SELECT set_config('etaye.practice_id', etaye_demo.fixture_id('PRA-A')::text, true)");
            invalidLab(connection, 1, 7, "Texte", null, null, "SRC-A-LAB-CORR-1", null, "2026-10-22T08:00:00Z", "23514");
            invalidLab(connection, 1, null, null, null, null, "SRC-A-LAB-CORR-1", null, "2026-10-22T08:00:00Z", "23514");
            invalidLab(connection, 1, 7, null, 9, 2, "SRC-A-LAB-CORR-1", null, "2026-10-22T08:00:00Z", "23514");
            invalidLab(connection, 1, 7, null, null, null, "SRC-A-002", null, "2026-10-22T08:00:00Z", "23503");
            invalidLab(connection, 1, 7, null, null, null, "SRC-A-LAB-CORR-1", null, "2026-10-20T08:00:00Z", "23514");
            invalidLab(connection, 3, 7, null, null, null, "SRC-A-LAB-CORR-1", "LAB-A-PROOF-1", "2026-10-22T08:00:00Z", "23514");
            invalidLab(connection, 2, 7, null, null, null, "SRC-A-LAB-CORR-1", "absent-lab", "2026-10-22T08:00:00Z", "23514");
            assertThatThrownBy(() -> command.executeUpdate("""
                    INSERT INTO public.lab_result (id, practice_id, record_id, lineage_id, revision, analyte, numeric_value,
                        source_revision_id, supersedes_id, correction_reason, author_label, recorded_at)
                    VALUES (etaye_demo.fixture_id('test-self-reference'), etaye_demo.fixture_id('PRA-A'),
                        etaye_demo.fixture_id('R-A-003'), gen_random_uuid(), 2, 'Analyse A', 7,
                        etaye_demo.fixture_id('SRC-A-LAB-CORR-1'), etaye_demo.fixture_id('test-self-reference'),
                        'Correction fictive', 'Dr Essai', '2026-10-22T08:00:00Z');
                    """)).isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23514"));
        }
    }

    private static void invalidLab(Connection connection, int revision, Integer number, String text, Integer lower, Integer upper,
                                   String source, String predecessor, String recorded, String state) throws SQLException {
        var before = connection.setSavepoint();
        try (var command = connection.prepareStatement("""
                INSERT INTO public.lab_result (practice_id, record_id, lineage_id, revision, analyte, numeric_value,
                    qualitative_value, lower_bound, upper_bound, source_revision_id, supersedes_id, correction_reason, author_label, recorded_at)
                SELECT etaye_demo.fixture_id('PRA-A'), etaye_demo.fixture_id('R-A-003'),
                    coalesce(etaye_demo.fixture_id(?), gen_random_uuid()), ?, 'Analyse A', ?, ?, ?, ?,
                    etaye_demo.fixture_id(?), etaye_demo.fixture_id(?), ?, 'Dr Essai', ?::timestamptz
                """)) {
            command.setString(1, predecessor == null ? null : "lineage/LAB-A-PROOF");
            command.setInt(2, revision);
            command.setObject(3, number);
            command.setString(4, text);
            command.setObject(5, lower);
            command.setObject(6, upper);
            command.setString(7, source);
            command.setString(8, predecessor);
            command.setString(9, predecessor == null ? null : "Correction fictive");
            command.setString(10, recorded);
            assertThatThrownBy(command::executeUpdate).isInstanceOfSatisfying(SQLException.class,
                    error -> assertThat(error.getSQLState()).isEqualTo(state));
        } finally {
            connection.rollback(before);
        }
    }

    @Test
    void accountAndPracticeBoundariesApplyToAllThreeReads() {
        String nora = login("nora.lefebvre@example.test");
        String mila = login("mila.dubois@example.test");
        String patient = login("claire.martin@example.test");
        String resultId = items(get(nora, list(marie))).getFirst().get("id").toString();
        for (String suffix : List.of("", "/analytes", "/" + resultId + "/revisions")) {
            assertThat(get(null, list(marie) + suffix).getStatusCode().value()).isEqualTo(401);
            assertThat(get(mila, list(marie) + suffix).getStatusCode().value()).isEqualTo(403);
            assertThat(get(patient, list(marie) + suffix).getStatusCode().value()).isEqualTo(404);
            var foreign = get(nora, list(hugo) + suffix);
            var absent = get(nora, list(UUID.randomUUID().toString()) + suffix);
            assertThat(foreign.getStatusCode().value()).isEqualTo(404);
            assertThat(withoutCorrelation(foreign.getBody())).isEqualTo(withoutCorrelation(absent.getBody()));
            assertThat(get(nora, list(hugo).replace(practiceA, practiceB) + suffix).getStatusCode().value()).isEqualTo(404);
        }
    }
}
