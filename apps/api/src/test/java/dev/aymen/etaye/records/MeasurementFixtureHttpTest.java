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
 * The measurement reads of story 3.3 over the real synthetic fixture (Claire, Louis, Hugo, Mila) on the real HTTP
 * stack and PostgreSQL: AT-011-A (dated measurements with value, unit and provenance, the weight before and after its
 * correction in the asOf view) and AT-011-B (the old revision and its reason stay readable, missing values are not
 * invented, nobody outside the practice or without a clinical grant reads them).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "etaye.demo.enabled=true")
@ActiveProfiles("test")
class MeasurementFixtureHttpTest {

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

    private static List<String> values(ResponseEntity<Map<String, Object>> response) {
        return items(response).stream().map(item -> item.get("concept") + " " + item.get("value") + " " + item.get("unit")).toList();
    }

    private static Map<String, Object> withoutCorrelation(Map<String, Object> problem) {
        var copy = new java.util.HashMap<>(problem);
        copy.remove("correlationId");
        return copy;
    }

    private String list(String record) {
        return "/practices/" + practiceA + "/records/" + record + "/measurements";
    }

    @Test
    @DisplayName("AT-011-A: Claire's measurements carry value, unit, observed time and provenance, and the weight is the corrected revision")
    void claireMeasurementsExposeDatedValuesWithProvenance() {
        String nora = login("nora.lefebvre@example.test");
        var response = get(nora, list(claire));
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getETag()).isNull();
        assertThat(response.getBody()).containsEntry("nextCursor", null);
        var items = items(response);
        // One item per lineage: five current revisions (two readings of two components, one weight).
        assertThat(items).hasSize(5);
        assertThat(items.getFirst()).containsEntry("concept", "WEIGHT").containsEntry("value", "68.4").containsEntry("unit", "kg")
                .containsEntry("revision", 2).containsEntry("sourceReference", "SRC-A-007-v2")
                .containsEntry("sourceType", "PRACTITIONER_OBSERVED").containsEntry("authorLabel", "Dr Nora Lefebvre");
        assertThat(items.getFirst().get("correctionReason").toString()).isEqualTo("Correction d’une erreur de saisie, 86,4 devient 68,4 kg.");
        assertThat(items.getFirst().get("observedAt").toString()).startsWith("2026-10-20T08:12");
        assertThat(items.getFirst().get("recordedAt").toString()).startsWith("2026-10-20T08:15");
        assertThat(items.getFirst()).doesNotContainKey("groupId");
        // The newest reading, then the older one; the two components of a reading share a group and an instant.
        assertThat(values(response).subList(1, 5)).containsExactlyInAnyOrder(
                "SYSTOLIC_BLOOD_PRESSURE 134 mmHg", "DIASTOLIC_BLOOD_PRESSURE 82 mmHg",
                "SYSTOLIC_BLOOD_PRESSURE 128 mmHg", "DIASTOLIC_BLOOD_PRESSURE 80 mmHg");
        var byGroup = new java.util.HashMap<Object, List<Map<String, Object>>>();
        items.subList(1, 5).forEach(item -> byGroup.computeIfAbsent(item.get("groupId"), key -> new ArrayList<>()).add(item));
        assertThat(byGroup).hasSize(2);
        byGroup.values().forEach(group -> {
            assertThat(group).hasSize(2);
            assertThat(group.get(0).get("observedAt")).isEqualTo(group.get(1).get("observedAt"));
        });
        assertThat(items.subList(1, 3)).extracting(item -> item.get("observedAt").toString().substring(0, 16)).containsOnly("2026-10-20T08:10");
        assertThat(items.subList(3, 5)).extracting(item -> item.get("observedAt").toString().substring(0, 16)).containsOnly("2026-09-14T07:35");
    }

    @Test
    @DisplayName("AT-011-A, AT-011-B: the lineage keeps the original and its reason, and the asOf view shows the weight before and after the correction")
    void theWeightLineageAndTheAsOfView() {
        String nora = login("nora.lefebvre@example.test");
        var current = items(get(nora, list(claire))).getFirst();
        String lineage = "/practices/" + practiceA + "/measurements/" + current.get("id") + "/revisions";
        var revisions = items(get(nora, lineage));
        assertThat(revisions).extracting(item -> item.get("value")).containsExactly("86.4", "68.4");
        assertThat(revisions.getFirst()).doesNotContainKeys("correctionReason", "supersedesId");
        assertThat(revisions.getFirst().get("sourceReference")).isEqualTo("SRC-A-007-v1");
        assertThat(revisions.getLast()).containsEntry("supersedesId", revisions.getFirst().get("id"));

        // 20/10/2026 10:14 and 10:16 in Paris (UTC+2): the correction was recorded at 10:15.
        var before = get(nora, list(claire) + "?asOf=2026-10-20T10:14:00%2B02:00");
        assertThat(values(before)).contains("WEIGHT 86.4 kg").doesNotContain("WEIGHT 68.4 kg");
        // The view says nothing about a correction: no reason, no superseded identifier, and the text of the time.
        var weightBefore = items(before).stream().filter(item -> "WEIGHT".equals(item.get("concept"))).findFirst().orElseThrow();
        assertThat(weightBefore).containsEntry("revision", 1).doesNotContainKeys("correctionReason", "supersedesId");
        assertThat(get(nora, list(claire) + "?asOf=2026-10-20T10:14:00%2B02:00").getBody().toString()).doesNotContain("68.4");
        var after = get(nora, list(claire) + "?asOf=2026-10-20T10:16:00%2B02:00");
        assertThat(values(after)).contains("WEIGHT 68.4 kg").doesNotContain("WEIGHT 86.4 kg");
        // The lineage read obeys the same rule: the corrected revision is a 404 before it was recorded.
        var hidden = get(nora, lineage + "?asOf=2026-10-20T10:14:00%2B02:00");
        assertThat(hidden.getStatusCode().value()).isEqualTo(404);
        assertThat(withoutCorrelation(hidden.getBody())).isEqualTo(withoutCorrelation(
                get(nora, "/practices/" + practiceA + "/measurements/" + UUID.randomUUID() + "/revisions?asOf=2026-10-20T10:14:00%2B02:00").getBody()));
        var earlier = items(get(nora, "/practices/" + practiceA + "/measurements/" + revisions.getFirst().get("id")
                + "/revisions?asOf=2026-10-20T10:14:00%2B02:00"));
        assertThat(earlier).extracting(item -> item.get("value")).containsExactly("86.4");
        // Before the first recording (14/09/2026 09:36) nothing is known: an empty list, never a value.
        assertThat(items(get(nora, list(claire) + "?asOf=2026-09-01T08:00:00%2B02:00"))).isEmpty();
        // Between the two readings only the first is known.
        assertThat(values(get(nora, list(claire) + "?asOf=2026-10-01T00:00:00%2B02:00")))
                .containsExactlyInAnyOrder("SYSTOLIC_BLOOD_PRESSURE 128 mmHg", "DIASTOLIC_BLOOD_PRESSURE 80 mmHg");
        assertThat(get(nora, list(claire) + "?asOf=2999-01-01T00:00:00Z").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("AT-011-B: a record without measurement lists nothing, and a concept filter keeps the exact stored codes")
    void louisHasNoMeasurementAndFiltersAreExact() {
        String nora = login("nora.lefebvre@example.test");
        var louisList = get(nora, list(louis));
        assertThat(items(louisList)).isEmpty();
        assertThat(louisList.getBody()).containsEntry("nextCursor", null);
        assertThat(values(get(nora, list(claire) + "?concept=WEIGHT"))).containsExactly("WEIGHT 68.4 kg");
        assertThat(items(get(nora, list(claire) + "?concept=SYSTOLIC_BLOOD_PRESSURE&concept=DIASTOLIC_BLOOD_PRESSURE"))).hasSize(4);
        assertThat(items(get(nora, list(claire) + "?concept=weight"))).isEmpty();
        // The observed-time bounds: from inclusive, to exclusive.
        assertThat(items(get(nora, list(claire) + "?from=2026-10-01T00:00:00Z"))).hasSize(3);
        assertThat(items(get(nora, list(claire) + "?to=2026-10-20T08:10:00Z"))).hasSize(2);
        assertThat(items(get(nora, list(claire) + "?from=2026-10-20T08:10:00Z&to=2026-10-20T08:12:00Z"))).hasSize(2);
    }

    @Test
    @DisplayName("AT-011-B isolation: a foreign record, a guessed measurement and a foreign cursor are 404 or 400 like an absent resource")
    void foreignRecordsAndMeasurementsAreHidden() {
        String nora = login("nora.lefebvre@example.test");
        var viaOwnRoute = get(nora, list(hugo));
        var absent = get(nora, list(UUID.randomUUID().toString()));
        assertThat(viaOwnRoute.getStatusCode().value()).isEqualTo(404);
        assertThat(withoutCorrelation(viaOwnRoute.getBody())).isEqualTo(withoutCorrelation(absent.getBody()));
        assertThat(get(nora, "/practices/" + practiceB + "/records/" + hugo + "/measurements").getStatusCode().value()).isEqualTo(404);
        // A cursor issued for one record is refused on another and is not a way to read a foreign record.
        var firstPage = get(nora, list(claire) + "?limit=2");
        String cursor = (String) firstPage.getBody().get("nextCursor");
        assertThat(cursor).isNotBlank();
        assertThat(get(nora, list(louis) + "?cursor=" + cursor).getStatusCode().value()).isEqualTo(400);
        assertThat(get(nora, list(claire) + "?concept=WEIGHT&cursor=" + cursor).getStatusCode().value()).isEqualTo(400);
        assertThat(get(login("sami.rahmani@example.test"), list(claire) + "?cursor=" + cursor).getStatusCode().value()).isEqualTo(400);
        // Sami also works in practice B: Claire's measurement identifier is not readable through B's route, and the
        // route practice never grants authority over another practice's record.
        String claireWeight = items(get(nora, list(claire))).getFirst().get("id").toString();
        String sami = login("sami.rahmani@example.test");
        assertThat(get(sami, "/practices/" + practiceB + "/measurements/" + claireWeight + "/revisions").getStatusCode().value()).isEqualTo(404);
        assertThat(get(nora, "/practices/" + practiceB + "/measurements/" + claireWeight + "/revisions").getStatusCode().value()).isEqualTo(404);
        assertThat(get(sami, "/practices/" + practiceA + "/measurements/" + claireWeight + "/revisions").getStatusCode().value()).isEqualTo(200);
        assertThat(get(sami, "/practices/" + practiceA + "/records/" + hugo + "/measurements").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("An administration-only account is 403 and a portal-only account has no clinical route")
    void restrictedAccountsAreRefused() {
        String nora = login("nora.lefebvre@example.test");
        String claireWeight = items(get(nora, list(claire))).getFirst().get("id").toString();
        String mila = login("mila.dubois@example.test");
        assertThat(get(mila, list(claire)).getStatusCode().value()).isEqualTo(403);
        assertThat(get(mila, "/practices/" + practiceA + "/measurements/" + claireWeight + "/revisions").getStatusCode().value()).isEqualTo(403);
        String portal = login("claire.martin@example.test");
        assertThat(get(portal, list(claire)).getStatusCode().value()).isEqualTo(404);
        assertThat(get(portal, "/practices/" + practiceA + "/measurements/" + claireWeight + "/revisions").getStatusCode().value()).isEqualTo(404);
        assertThat(get(null, list(claire)).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("D-64: the session of Nora carries the time zone of her practice and Claire's portal context carries none")
    void sessionContextsCarryTheTimeZone() {
        var nora = get(login("nora.lefebvre@example.test"), "/session");
        @SuppressWarnings("unchecked")
        var contexts = (List<Map<String, Object>>) nora.getBody().get("contexts");
        assertThat(contexts).singleElement().satisfies(context -> assertThat(context).containsEntry("timezone", "Europe/Paris"));
        var claireSession = get(login("claire.martin@example.test"), "/session");
        @SuppressWarnings("unchecked")
        var portalContexts = (List<Map<String, Object>>) claireSession.getBody().get("contexts");
        assertThat(portalContexts).singleElement().satisfies(context -> assertThat(context).containsEntry("kind", "PORTAL").doesNotContainKey("timezone"));
    }
}
