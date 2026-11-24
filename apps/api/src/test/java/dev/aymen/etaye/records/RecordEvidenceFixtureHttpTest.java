package dev.aymen.etaye.records;

import dev.aymen.etaye.infrastructure.demo.DemoDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The record evidence reads of story 3.2 over the real synthetic fixture (Claire, Louis, Hugo) on the real HTTP
 * stack and PostgreSQL: AT-010-A (dated history, allergies and sources with their types, the asOf view) and AT-010-B
 * (a thin record shows unknown or not recorded, conflicting reports stay visible).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "etaye.demo.enabled=true")
@ActiveProfiles("test")
class RecordEvidenceFixtureHttpTest {

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

    @BeforeAll
    static void identifiers() throws SQLException {
        practiceA = value("SELECT etaye_demo.fixture_id('PRA-A')");
        practiceB = value("SELECT etaye_demo.fixture_id('PRA-B')");
        claire = value("SELECT etaye_demo.fixture_id('R-A-001')");
        louis = value("SELECT etaye_demo.fixture_id('R-A-002')");
        hugo = value("SELECT etaye_demo.fixture_id('R-B-001')");
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

    private static List<String> references(ResponseEntity<Map<String, Object>> response) {
        return items(response).stream().map(item -> item.get("reference").toString()).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(ResponseEntity<Map<String, Object>> history, String category) {
        assertThat(history.getStatusCode().value()).isEqualTo(200);
        return ((List<Map<String, Object>>) history.getBody().get("sections")).stream()
                .filter(s -> category.equals(s.get("category"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> statements(Map<String, Object> section) {
        return (List<Map<String, Object>>) section.get("statements");
    }

    @Test
    @DisplayName("AT-010-A: the rich record lists dated evidence, newest event first, with both dates, author and origin")
    void claireTimelineExposesDatedHistoryWithSourceTypes() {
        String nora = login("nora.lefebvre@example.test");
        var response = get(nora, "/practices/" + practiceA + "/records/" + claire + "/timeline");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getETag()).isNull();
        assertThat(response.getBody()).containsEntry("nextCursor", null);
        // One item per source lineage: the weight pair is one item, the current revision.
        assertThat(references(response)).containsExactly("SRC-A-005", "SRC-A-006", "SRC-A-007-v2", "SRC-A-004",
                "SRC-A-003", "SRC-A-008", "SRC-A-001", "SRC-A-002");
        var preparation = items(response).getFirst();
        assertThat(preparation).containsEntry("kind", "PATIENT_PREPARATION")
                .containsEntry("sourceType", "PATIENT_REPORTED").containsEntry("authorLabel", "Claire Martin (patiente)")
                .containsEntry("label", "Reçue de Claire Martin").containsEntry("corrected", false);
        assertThat(preparation.get("excerpt").toString()).contains("pénicilline");
        var weight = items(response).get(2);
        assertThat(weight).containsEntry("label", "Poids 68,4 kg").containsEntry("corrected", true)
                .containsEntry("revision", 2).containsEntry("sourceType", "PRACTITIONER_OBSERVED");
        assertThat(weight.get("correctionReason").toString()).contains("86,4 devient 68,4 kg");
        // The occurrence and the recording dates are separate facts.
        var lab = items(response).getLast();
        assertThat(((Map<?, ?>) lab.get("eventDate")).get("instant").toString()).startsWith("2026-09-14T06:00");
        assertThat(lab.get("recordedAt").toString()).startsWith("2026-09-15T08:00");
        assertThat(lab).containsEntry("sourceType", "DOCUMENT_REPORTED");
    }

    @Test
    @DisplayName("AT-010-A: allergies stay conflicting with both dated statements, treatments are unknown, history is not recorded")
    void claireHistoryKeepsConflictingReportsVisible() {
        String nora = login("nora.lefebvre@example.test");
        var history = get(nora, "/practices/" + practiceA + "/records/" + claire + "/history");
        assertThat(history.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(history.getBody()).containsEntry("asOf", null);
        var allergies = section(history, "ALLERGY");
        assertThat(allergies).containsEntry("state", "CONFLICTING").containsEntry("truncated", false);
        var both = statements(allergies);
        assertThat(both).extracting(s -> s.get("assertionKind")).containsExactly("EXPLICITLY_ABSENT", "PRESENT");
        assertThat(both.get(0)).containsEntry("sourceReference", "SRC-A-001").containsEntry("sourceType", "PATIENT_REPORTED")
                .containsEntry("sourceKind", "CONSULTATION_NOTE")
                .containsEntry("authorLabel", "Dr Nora Lefebvre");
        assertThat(both.get(1)).containsEntry("sourceReference", "SRC-A-005").containsEntry("authorLabel", "Claire Martin (patiente)");
        assertThat(both.get(1).get("text").toString()).contains("pénicilline");
        assertThat(section(history, "HISTORY")).containsEntry("state", "NOT_RECORDED");
        assertThat(statements(section(history, "HISTORY"))).isEmpty();
        var treatments = section(history, "REPORTED_TREATMENT");
        assertThat(treatments).containsEntry("state", "UNKNOWN");
        // An unknown statement has no text, no source and no date: the fields are absent, not an empty value.
        assertThat(statements(treatments)).hasSize(1);
        assertThat(statements(treatments).getFirst()).containsEntry("assertionKind", "UNKNOWN")
                .doesNotContainKeys("text", "sourceRevisionId", "sourceType", "sourceReference", "sourceKind", "correctionReason");
        assertThat(((Map<?, ?>) statements(treatments).getFirst().get("eventDate"))).isEqualTo(Map.of("precision", "UNKNOWN"));
    }

    @Test
    @DisplayName("AT-010-B: a thin record shows unknown or not recorded for every section and no event, never an absence")
    void louisIsThinNeverNormal() {
        String nora = login("nora.lefebvre@example.test");
        assertThat(items(get(nora, "/practices/" + practiceA + "/records/" + louis + "/timeline"))).isEmpty();
        var history = get(nora, "/practices/" + practiceA + "/records/" + louis + "/history");
        assertThat(section(history, "ALLERGY")).containsEntry("state", "NOT_RECORDED");
        assertThat(section(history, "HISTORY")).containsEntry("state", "UNKNOWN");
        assertThat(section(history, "REPORTED_TREATMENT")).containsEntry("state", "NOT_RECORDED");
        assertThat(items(get(nora, "/practices/" + practiceA + "/records/" + louis + "/assertions"))).hasSize(1);
        assertThat(items(get(nora, "/practices/" + practiceA + "/records/" + louis + "/sources"))).isEmpty();
    }

    @Test
    @DisplayName("UD-32: an asOf view never exposes a later revision, and the weight corrects at 10:15")
    void asOfViewHidesLaterRevisions() {
        String nora = login("nora.lefebvre@example.test");
        String base = "/practices/" + practiceA + "/records/" + claire;
        // 2026-11-01: the patient preparation (recorded 2 November 09:10) does not exist yet.
        var before = get(nora, base + "/timeline?asOf=2026-11-01T00:00:00Z");
        assertThat(references(before)).doesNotContain("SRC-A-005").hasSize(7);
        var allergies = section(get(nora, base + "/history?asOf=2026-11-01T00:00:00Z"), "ALLERGY");
        assertThat(allergies).containsEntry("state", "EXPLICITLY_ABSENT");
        assertThat(statements(allergies)).hasSize(1);
        // The unknown rows are recorded at the dataset baseline: before it, the treatments are simply not recorded.
        assertThat(section(get(nora, base + "/history?asOf=2026-11-01T00:00:00Z"), "REPORTED_TREATMENT"))
                .containsEntry("state", "NOT_RECORDED");

        // 2026-10-20 10:14 Paris: only version 1 of the weight, with its contemporaneous text.
        var between = get(nora, base + "/timeline?asOf=2026-10-20T10:14:00%2B02:00");
        var weight = items(between).stream().filter(i -> i.get("reference").toString().startsWith("SRC-A-007")).findFirst().orElseThrow();
        assertThat(weight).containsEntry("reference", "SRC-A-007-v1").containsEntry("label", "Poids 86,4 kg")
                .containsEntry("corrected", false).doesNotContainKey("correctionReason");
        String sourceId = weight.get("sourceRevisionId").toString();
        var source = get(nora, "/practices/" + practiceA + "/sources/" + sourceId + "?asOf=2026-10-20T10:14:00%2B02:00");
        assertThat(source.getBody().get("excerpt")).isEqualTo("Poids saisi : 86,4 kg.");
        assertThat(source.getBody().toString()).doesNotContain("corrigé").doesNotContain("68,4").doesNotContain("SRC-A-007-v2");
        // At 10:16 the corrected revision is the current one.
        var after = get(nora, base + "/timeline?asOf=2026-10-20T10:16:00%2B02:00");
        assertThat(references(after)).contains("SRC-A-007-v2").doesNotContain("SRC-A-007-v1");

        // Before the first record nothing is known, and nothing reads as normal.
        var nothing = get(nora, base + "/timeline?asOf=2026-09-01T00:00:00Z");
        assertThat(items(nothing)).isEmpty();
        for (String category : List.of("ALLERGY", "HISTORY", "REPORTED_TREATMENT")) {
            assertThat(section(get(nora, base + "/history?asOf=2026-09-01T00:00:00Z"), category)).containsEntry("state", "NOT_RECORDED");
        }
    }

    @Test
    @DisplayName("UD-32: a revision recorded after the instant is a 404 byte-identical to an absent source")
    void sourceRevisionRecordedLaterIsHiddenInThePastView() {
        String nora = login("nora.lefebvre@example.test");
        var current = get(nora, "/practices/" + practiceA + "/records/" + claire + "/timeline?asOf=2026-10-20T10:16:00%2B02:00");
        String weightV2 = items(current).stream().filter(i -> "SRC-A-007-v2".equals(i.get("reference"))).findFirst()
                .orElseThrow().get("sourceRevisionId").toString();
        var visible = get(nora, "/practices/" + practiceA + "/sources/" + weightV2);
        assertThat(visible.getStatusCode().value()).isEqualTo(200);
        assertThat(visible.getBody()).containsEntry("supersedesReference", "SRC-A-007-v1").containsKey("supersedesId")
                .containsEntry("kind", "PRACTITIONER_MEASUREMENT");
        var hidden = get(nora, "/practices/" + practiceA + "/sources/" + weightV2 + "?asOf=2026-10-20T10:14:00%2B02:00");
        var absent = get(nora, "/practices/" + practiceA + "/sources/" + UUID.randomUUID() + "?asOf=2026-10-20T10:14:00%2B02:00");
        assertThat(hidden.getStatusCode().value()).isEqualTo(404);
        assertThat(withoutCorrelation(hidden.getBody())).isEqualTo(withoutCorrelation(absent.getBody()));
    }

    private static Map<String, Object> withoutCorrelation(Map<String, Object> problem) {
        var copy = new java.util.HashMap<>(problem);
        copy.remove("correlationId");
        return copy;
    }

    @Test
    @DisplayName("A future instant, a malformed instant and an unknown filter are 400")
    void invalidViewParametersAreRefused() {
        String nora = login("nora.lefebvre@example.test");
        String base = "/practices/" + practiceA + "/records/" + claire;
        assertThat(get(nora, base + "/timeline?asOf=2099-01-01T00:00:00Z").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "/history?asOf=2099-01-01T00:00:00Z").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "/assertions?asOf=2099-01-01T00:00:00Z").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "/sources?asOf=2099-01-01T00:00:00Z").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, "/practices/" + practiceA + "/sources/" + UUID.randomUUID() + "?asOf=2099-01-01T00:00:00Z")
                .getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "/timeline?asOf=yesterday").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "/timeline?asOf=2026-10-20T10:14:00").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "/timeline?kind=SECRET").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "/assertions?category=ALLERGIES").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "/timeline?from=2026-10-20T00:00:00Z&to=2026-10-01T00:00:00Z").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "/timeline?limit=0").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, base + "/timeline?limit=101").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("Filters: kind and event-date bounds apply to the event date; unknown kinds are refused")
    void kindAndDateFilters() {
        String nora = login("nora.lefebvre@example.test");
        String base = "/practices/" + practiceA + "/records/" + claire + "/timeline";
        assertThat(references(get(nora, base + "?kind=PRACTITIONER_MEASUREMENT"))).containsExactly("SRC-A-007-v2", "SRC-A-004", "SRC-A-008");
        assertThat(references(get(nora, base + "?kind=MANUAL_LAB_PANEL&kind=UPLOADED_DOCUMENT"))).containsExactly("SRC-A-003", "SRC-A-002");
        // from is inclusive and to exclusive, on the event date: 20 October (Paris) holds three items.
        assertThat(references(get(nora, base + "?from=2026-10-20T00:00:00%2B02:00&to=2026-10-21T00:00:00%2B02:00")))
                .containsExactly("SRC-A-006", "SRC-A-007-v2", "SRC-A-004");
        assertThat(references(get(nora, base + "?to=2026-10-20T10:10:00%2B02:00"))).doesNotContain("SRC-A-004")
                .contains("SRC-A-003");
        assertThat(references(get(nora, base + "?from=2026-10-20T10:10:00%2B02:00&to=2026-10-20T10:11:00%2B02:00")))
                .containsExactly("SRC-A-004");
    }

    @Test
    @DisplayName("Pages of 25 by default: three items per page walk all eight in order without repeats")
    void cursorWalksTheTimeline() {
        String nora = login("nora.lefebvre@example.test");
        String base = "/practices/" + practiceA + "/records/" + claire + "/timeline";
        List<String> walked = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            var page = get(nora, base + "?limit=3" + (cursor == null ? "" : "&cursor=" + cursor));
            walked.addAll(references(page));
            cursor = (String) page.getBody().get("nextCursor");
            pages++;
        } while (cursor != null && pages < 10);
        assertThat(pages).isEqualTo(3);
        assertThat(walked).containsExactly("SRC-A-005", "SRC-A-006", "SRC-A-007-v2", "SRC-A-004", "SRC-A-003", "SRC-A-008",
                "SRC-A-001", "SRC-A-002");
    }

    @Test
    @DisplayName("AT-010 isolation: the foreign record, a guessed source and a foreign cursor are 404 or 400 like an absent resource")
    void foreignRecordsAndSourcesAreHidden() {
        String nora = login("nora.lefebvre@example.test");
        // Hugo belongs to the other practice: through the route of Nora's practice and through the other practice.
        var viaOwnRoute = get(nora, "/practices/" + practiceA + "/records/" + hugo + "/timeline");
        var absent = get(nora, "/practices/" + practiceA + "/records/" + UUID.randomUUID() + "/timeline");
        assertThat(viaOwnRoute.getStatusCode().value()).isEqualTo(404);
        assertThat(withoutCorrelation(viaOwnRoute.getBody())).isEqualTo(withoutCorrelation(absent.getBody()));
        for (String suffix : List.of("/timeline", "/sources", "/assertions", "/history")) {
            assertThat(get(nora, "/practices/" + practiceA + "/records/" + hugo + suffix).getStatusCode().value()).as(suffix).isEqualTo(404);
            assertThat(get(nora, "/practices/" + practiceB + "/records/" + hugo + suffix).getStatusCode().value()).as("B" + suffix).isEqualTo(404);
        }
        // Hugo's source exists, but not for Nora's practice route or membership.
        String hugoSource = null;
        String sami = login("sami.rahmani@example.test");
        var samiView = get(sami, "/practices/" + practiceB + "/records/" + hugo + "/timeline");
        assertThat(references(samiView)).containsExactly("SRC-B-001");
        hugoSource = items(samiView).getFirst().get("sourceRevisionId").toString();
        assertThat(get(nora, "/practices/" + practiceA + "/sources/" + hugoSource).getStatusCode().value()).isEqualTo(404);
        assertThat(get(nora, "/practices/" + practiceB + "/sources/" + hugoSource).getStatusCode().value()).isEqualTo(404);
        assertThat(get(sami, "/practices/" + practiceB + "/sources/" + hugoSource).getStatusCode().value()).isEqualTo(200);
        // Sami also works in practice A but the route practice never grants authority over another practice's record.
        assertThat(get(sami, "/practices/" + practiceA + "/records/" + hugo + "/timeline").getStatusCode().value()).isEqualTo(404);
        // A cursor issued for one record is refused on another and is not a way to read a foreign record.
        var firstPage = get(nora, "/practices/" + practiceA + "/records/" + claire + "/timeline?limit=2");
        String cursor = (String) firstPage.getBody().get("nextCursor");
        assertThat(get(nora, "/practices/" + practiceA + "/records/" + louis + "/timeline?cursor=" + cursor).getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, "/practices/" + practiceA + "/records/" + claire + "/timeline?cursor=" + cursor + "x").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, "/practices/" + practiceA + "/records/" + claire + "/timeline?cursor=" + cursor
                + "&asOf=2026-11-01T00:00:00Z").getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, "/practices/" + practiceA + "/records/" + claire + "/timeline?cursor=" + cursor
                + "&kind=MANUAL_LAB_PANEL").getStatusCode().value()).isEqualTo(400);
        // The same cursor under another account is refused too.
        String otherCookie = login("sami.rahmani@example.test");
        assertThat(get(otherCookie, "/practices/" + practiceA + "/records/" + claire + "/timeline?cursor=" + cursor).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("An administration-only account is 403 and a portal-only account has no clinical route")
    void restrictedAccountsAreRefused() {
        String mila = login("mila.dubois@example.test");
        for (String suffix : List.of("/timeline", "/sources", "/assertions", "/history")) {
            assertThat(get(mila, "/practices/" + practiceA + "/records/" + claire + suffix).getStatusCode().value()).as(suffix).isEqualTo(403);
        }
        assertThat(get(mila, "/practices/" + practiceA + "/sources/" + UUID.randomUUID()).getStatusCode().value()).isEqualTo(403);
        String portal = login("claire.martin@example.test");
        for (String suffix : List.of("/timeline", "/sources", "/assertions", "/history")) {
            assertThat(get(portal, "/practices/" + practiceA + "/records/" + claire + suffix).getStatusCode().value()).as(suffix).isEqualTo(404);
        }
        assertThat(get(null, "/practices/" + practiceA + "/records/" + claire + "/timeline").getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("The assertion list filters by category and lists UNKNOWN statements like the others")
    void assertionsListByCategory() {
        String nora = login("nora.lefebvre@example.test");
        String base = "/practices/" + practiceA + "/records/" + claire + "/assertions";
        var all = items(get(nora, base));
        assertThat(all).hasSize(3);
        assertThat(all).extracting(a -> a.get("assertionKind")).containsExactly("EXPLICITLY_ABSENT", "PRESENT", "UNKNOWN");
        assertThat(items(get(nora, base + "?category=ALLERGY"))).hasSize(2);
        assertThat(items(get(nora, base + "?category=HISTORY"))).isEmpty();
        assertThat(items(get(nora, base + "?category=REPORTED_TREATMENT"))).hasSize(1);
        assertThat(items(get(nora, base + "?asOf=2026-11-01T00:00:00Z"))).hasSize(1);
        var paged = get(nora, base + "?limit=1");
        assertThat(paged.getBody().get("nextCursor")).isNotNull();
        assertThat(items(get(nora, base + "?limit=1&cursor=" + paged.getBody().get("nextCursor"))).getFirst())
                .containsEntry("assertionKind", "PRESENT");
    }
}
