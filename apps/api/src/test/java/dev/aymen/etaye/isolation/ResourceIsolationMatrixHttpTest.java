package dev.aymen.etaye.isolation;

import dev.aymen.etaye.infrastructure.demo.DemoDatabase;
import dev.aymen.etaye.infrastructure.demo.DemoFixture;
import dev.aymen.etaye.infrastructure.security.AccountRequestBudget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adversarial proof of server-side resource isolation on the real record route of the synthetic fixture (story 1.4,
 * AT-004-A/B). Every actor signs in through the real session operation and every refusal is a real HTTP response.
 * The measured outcomes feed the report that CI publishes (UD-33, UD-59 C1).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "etaye.demo.enabled=true")
@ActiveProfiles("test")
@ExtendWith(IsolationMatrix.Extension.class)
class ResourceIsolationMatrixHttpTest {

    private static final String PASSWORD = "Etaye-Jeu-Fictif-91!";
    private static final String NORA = "nora.lefebvre@example.test";
    private static final String SAMI = "sami.rahmani@example.test";
    private static final String ELODIE = "elodie.morel@example.test";
    private static final String MILA = "mila.dubois@example.test";
    private static final String CLAIRE = "claire.martin@example.test";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Map<String, Object> NOT_FOUND = Map.of("type", "about:blank", "title", "Not Found",
            "status", 404, "code", "NOT_FOUND", "detail", "Resource not found");

    @DynamicPropertySource
    static void demoDatabase(DynamicPropertyRegistry registry) {
        DemoDatabase.configureDemo(registry);
    }

    @LocalServerPort private int port;
    @Autowired private DemoFixture fixture;
    @Autowired private AccountRequestBudget budget;
    @Autowired private ObjectMapper objectMapper;

    private String practiceA;
    private String practiceB;
    private String recordA;
    private String recordA2;
    private String recordB;

    private record Browser(String cookie) {}

    private record Exchange(int status, HttpHeaders headers, Map<String, Object> body) {
        String code() {
            return body == null ? null : (String) body.get("code");
        }

        String outcome() {
            return IsolationMatrix.outcome(status, code());
        }

        String fingerprint() {
            return IsolationMatrix.fingerprint(status, String.valueOf(headers.getContentType()),
                    headers.getCacheControl(), headers.headerNames(), body);
        }
    }

    @BeforeEach
    void restoreFixtureAndBudget() throws SQLException {
        // Each case signs in its own actors against a freshly restored fixture and the in-memory sign-in budget.
        ownerExecute("SELECT etaye_demo.restore_fixture()");
        budget.reset();
        practiceA = ownerValue("SELECT etaye_demo.fixture_id('PRA-A')");
        practiceB = ownerValue("SELECT etaye_demo.fixture_id('PRA-B')");
        recordA = ownerValue("SELECT etaye_demo.fixture_id('R-A-001')");
        recordA2 = ownerValue("SELECT etaye_demo.fixture_id('R-A-002')");
        recordB = ownerValue("SELECT etaye_demo.fixture_id('R-B-001')");
        IsolationMatrix.fixtureVersion(fixture.version().version());
    }

    @AfterEach
    void leaveFixtureRestored() throws SQLException {
        ownerExecute("SELECT etaye_demo.restore_fixture()");
    }

    @Test
    @DisplayName("AT-004-A: a practitioner reads the record of the own practice and the hidden record demonstrably exists")
    void practitionersReadTheirOwnRecords() {
        Exchange allowed = read(practiceA, recordA, login(NORA), null);

        assertThat(allowed.status()).isEqualTo(200);
        assertThat(allowed.headers().getETag()).isEqualTo("\"" + allowed.body().get("version") + "\"");
        assertThat(allowed.headers().getCacheControl()).contains("no-store");
        assertThat(allowed.body()).containsEntry("id", recordA).containsEntry("practiceId", practiceA);
        assertThat(patient(allowed)).containsEntry("firstName", "Claire").containsEntry("lastName", "Martin")
                .containsEntry("dateOfBirth", "1978-04-18");
        IsolationMatrix.observe("REC-A-ALLOWED", allowed.outcome());

        Exchange control = read(practiceB, recordB, login(ELODIE), null);
        assertThat(control.status()).isEqualTo(200);
        assertThat(control.body()).containsEntry("id", recordB).containsEntry("practiceId", practiceB);
        assertThat(patient(control)).containsEntry("firstName", "Hugo").containsEntry("lastName", "Petit");
        IsolationMatrix.observe("REC-B-CONTROL", control.outcome());

        Exchange conditional = read(practiceA, recordA, login(NORA), allowed.headers().getETag());
        assertThat(conditional.status()).isEqualTo(304);
        assertThat(conditional.headers().getETag()).isEqualTo(allowed.headers().getETag());
        assertThat(conditional.body()).isNull();
        IsolationMatrix.observe("CUR-CONDITIONAL-ALLOWED", conditional.outcome());
    }

    @Test
    @DisplayName("AT-004-B: a hidden, foreign, absent or unknown target gives one indistinguishable 404")
    void hiddenTargetsAreIndistinguishable() {
        Browser nora = login(NORA);
        String otherTag = read(practiceB, recordB, login(ELODIE), null).headers().getETag();
        Exchange foreignId = read(practiceA, recordB, nora, null);
        Exchange foreignPractice = read(practiceB, recordB, nora, null);
        Exchange missing = read(practiceA, UUID.randomUUID().toString(), nora, null);
        Exchange unknownPractice = read(UUID.randomUUID().toString(), recordA, nora, null);
        Exchange conditionalHidden = read(practiceA, recordB, nora, otherTag);

        Map<String, Exchange> hidden = new java.util.LinkedHashMap<>();
        hidden.put("REC-HIDDEN-FOREIGN-ID", foreignId);
        hidden.put("REC-HIDDEN-FOREIGN-PRACTICE", foreignPractice);
        hidden.put("REC-HIDDEN-MISSING", missing);
        hidden.put("REC-HIDDEN-UNKNOWN-PRACTICE", unknownPractice);
        hidden.put("CUR-CONDITIONAL-HIDDEN", conditionalHidden);
        hidden.forEach((id, response) -> {
            assertThat(response.headers().getContentType()).as(id).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(response.headers().getETag()).as("%s must carry no validator", id).isNull();
            assertThat(response.body()).as(id).containsEntry("correlationId", response.headers().getFirst("X-Correlation-Id"));
            Map<String, Object> problem = new java.util.LinkedHashMap<>(response.body());
            problem.remove("correlationId");
            assertThat(problem).as(id).isEqualTo(NOT_FOUND);
            IsolationMatrix.observe(id, response.outcome(), response.fingerprint());
        });
        assertThat(hidden.values().stream().map(Exchange::fingerprint).distinct()).hasSize(1);
    }

    @Test
    @DisplayName("AT-004-B: an administrator without a clinical capability is refused in the visible practice")
    void administratorIsRefusedWithoutDisclosure() throws SQLException {
        Exchange refusal = read(practiceA, recordA, login(MILA), null);

        assertThat(refusal.status()).isEqualTo(403);
        assertThat(refusal.headers().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(refusal.headers().getETag()).isNull();
        assertThat(refusal.body()).containsOnlyKeys("type", "title", "status", "code", "detail", "correlationId")
                .containsEntry("code", "FORBIDDEN").containsEntry("detail", "Access denied");
        assertThat(refusal.body().toString()).doesNotContain(recordA, practiceA);
        IsolationMatrix.observe("ADM-NO-CLINICAL", refusal.outcome());

        List<Map<String, Object>> events = oracleRows("""
                SELECT practice_id::text, actor_account_id::text, event_type, resource_type, resource_id::text,
                       outcome, safe_metadata::text FROM audit_event WHERE correlation_id = '%s'
                """.formatted(refusal.body().get("correlationId")));
        assertThat(events).hasSize(1);
        Map<String, Object> event = events.getFirst();
        assertThat(event).containsEntry("practice_id", practiceA).containsEntry("event_type", "ACCESS_DENIED")
                .containsEntry("resource_type", "API_OPERATION").containsEntry("outcome", "DENIED")
                .containsEntry("resource_id", null)
                .containsEntry("actor_account_id", ownerValue("SELECT etaye_demo.fixture_id('ACC-MILA')"));
        assertThat(objectMapper.readTree((String) event.get("safe_metadata"))).isEqualTo(objectMapper.valueToTree(Map.of(
                "reason", "CAPABILITY_MISSING", "capability", "CLINICAL_READ",
                "operation", "GET /api/v1/practices/{practiceId}/records/{recordId}")));
        assertThat(UUID_PATTERN.matcher((String) event.get("safe_metadata")).find()).isFalse();
        assertThat(event.get("safe_metadata").toString()).doesNotContain(recordA);
        IsolationMatrix.observe("ADM-DENIAL-AUDIT", event.get("event_type").equals("ACCESS_DENIED")
                ? "1 ACCESS_DENIED event without identifier" : "unexpected event");
    }

    @Test
    @DisplayName("AT-004-B: a linked patient is refused on the staff route and keeps the portal context")
    void linkedPatientCannotUseTheStaffRoute() throws SQLException {
        Browser claire = login(CLAIRE);
        String otherTag = read(practiceB, recordB, login(ELODIE), null).headers().getETag();
        assertThat(otherTag).isNotBlank();

        Exchange own = read(practiceA, recordA, claire, null);
        Exchange other = read(practiceA, recordA2, claire, null);
        Exchange baseline = read(practiceA, UUID.randomUUID().toString(), login(NORA), null);

        assertThat(own.fingerprint()).isEqualTo(baseline.fingerprint());
        assertThat(other.fingerprint()).isEqualTo(baseline.fingerprint());
        IsolationMatrix.observe("PAT-OWN-RECORD-STAFF-ROUTE", own.outcome(), own.fingerprint());
        IsolationMatrix.observe("PAT-OTHER-RECORD-STAFF-ROUTE", other.outcome(), other.fingerprint());

        Exchange session = get("/api/v1/session", claire, null);
        assertThat(session.status()).isEqualTo(200);
        List<?> contexts = (List<?>) session.body().get("contexts");
        boolean portal = contexts != null && contexts.stream()
                .anyMatch(context -> context instanceof Map<?, ?> map && "PORTAL".equals(map.get("kind")));
        IsolationMatrix.observe("PAT-PORTAL-CONTEXT-KEPT", portal ? "200 portal context kept" : "200 portal context missing");

        // A patient has no practice membership, so a refusal has no practice in which to be audited.
        assertThat(oracleRows("SELECT count(*) AS total FROM audit_event "
                + "WHERE actor_account_id = etaye_demo.fixture_id('ACC-CLAIRE')").getFirst()).containsEntry("total", "0");
    }

    @Test
    @DisplayName("AT-004-B: a missing, malformed or expired session is refused")
    void missingMalformedAndExpiredSessionsAreRefused() throws SQLException {
        Exchange anonymous = read(practiceA, recordA, null, null);
        assertThat(anonymous.status()).isEqualTo(401);
        assertThat(anonymous.headers().getFirst(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(anonymous.headers().getETag()).isNull();
        IsolationMatrix.observe("CUR-NO-SESSION", anonymous.outcome());

        Browser nora = login(NORA);
        assertThat(read(practiceA, recordA, nora, null).status()).isEqualTo(200);
        Exchange malformed = get("/api/v1/practices/" + practiceA + "/records/not-a-uuid", nora, null);
        assertThat(malformed.status()).isEqualTo(400);
        assertThat(malformed.headers().getETag()).isNull();
        IsolationMatrix.observe("INP-MALFORMED-ID", malformed.outcome());

        ownerExecute("UPDATE spring_session SET last_access_time = 0, max_inactive_interval = 1, expiry_time = 1");
        Exchange expired = read(practiceA, recordA, nora, null);
        assertThat(expired.status()).isEqualTo(401);
        assertThat(expired.headers().getETag()).isNull();
        assertThat(expired.body().toString()).doesNotContain(recordA, practiceA);
        IsolationMatrix.observe("CUR-EXPIRED-SESSION", expired.outcome());
    }

    @Test
    @DisplayName("AT-004-B: a suspended membership refuses the same session, even for a conditional read")
    void suspendedMembershipIsRefusedImmediately() throws SQLException {
        Browser nora = login(NORA);
        Exchange confirmed = read(practiceA, recordA, nora, null);
        assertThat(confirmed.status()).isEqualTo(200);
        String tag = confirmed.headers().getETag();

        ownerExecute("UPDATE membership SET state = 'SUSPENDED' WHERE id = etaye_demo.fixture_id('MEM-NORA-A')");

        Exchange refused = read(practiceA, recordA, nora, null);
        assertThat(refused.status()).isEqualTo(403);
        assertThat(refused.headers().getETag()).isNull();
        IsolationMatrix.observe("CUR-MEMBERSHIP-SUSPENDED", refused.outcome());

        Exchange conditional = read(practiceA, recordA, nora, tag);
        assertThat(conditional.status()).isNotEqualTo(304);
        assertThat(conditional.headers().getETag()).isNull();
        assertThat(conditional.body()).isNotNull();
        IsolationMatrix.observe("CUR-CONDITIONAL-REVOKED", conditional.outcome());
    }

    @Test
    @DisplayName("AT-004-B: a removed clinical capability refuses the same session")
    void removedCapabilityIsRefusedImmediately() throws SQLException {
        Browser nora = login(NORA);
        assertThat(read(practiceA, recordA, nora, null).status()).isEqualTo(200);

        ownerExecute("UPDATE membership SET capabilities = ARRAY['SCHEDULE_MANAGE'] "
                + "WHERE id = etaye_demo.fixture_id('MEM-NORA-A')");

        Exchange refused = read(practiceA, recordA, nora, null);
        assertThat(refused.status()).isEqualTo(403);
        assertThat(refused.headers().getETag()).isNull();
        IsolationMatrix.observe("CUR-CAPABILITY-REMOVED", refused.outcome());
    }

    @Test
    @DisplayName("AT-004-B: a suspended account loses its session on the next read")
    void suspendedAccountLosesTheSession() throws SQLException {
        Browser nora = login(NORA);
        assertThat(read(practiceA, recordA, nora, null).status()).isEqualTo(200);

        ownerExecute("UPDATE account SET state = 'SUSPENDED' WHERE id = etaye_demo.fixture_id('ACC-NORA')");

        Exchange refused = read(practiceA, recordA, nora, null);
        assertThat(refused.status()).isEqualTo(401);
        assertThat(refused.headers().getETag()).isNull();
        IsolationMatrix.observe("CUR-ACCOUNT-SUSPENDED", refused.outcome());
        // The only session this case created was closed by the refusal itself.
        assertThat(ownerValue("SELECT count(*) FROM spring_session")).isEqualTo("0");
    }

    @Test
    @DisplayName("AT-004-B: two memberships stay independent in their own contexts")
    void independentGrantsOfOneAccount() throws SQLException {
        Browser sami = login(SAMI);
        List<String> observed = new ArrayList<>();
        observed.add(read(practiceA, recordA, sami, null).outcome());
        observed.add(read(practiceB, recordB, sami, null).outcome());
        // The route practice never widens the grants: record B stays hidden through the practice A route.
        assertThat(read(practiceA, recordB, sami, null).status()).isEqualTo(404);

        ownerExecute("UPDATE membership SET state = 'SUSPENDED' WHERE id = etaye_demo.fixture_id('MEM-SAMI-B')");
        observed.add(read(practiceB, recordB, sami, null).outcome());
        observed.add(read(practiceA, recordA, sami, null).outcome());

        IsolationMatrix.observe("CTX-INDEPENDENT-GRANTS", String.join(", ", observed));
    }

    private Exchange read(String practiceId, String recordId, Browser browser, String ifNoneMatch) {
        return get("/api/v1/practices/" + practiceId + "/records/" + recordId, browser, ifNoneMatch);
    }

    private Exchange get(String path, Browser browser, String ifNoneMatch) {
        return exchange(HttpMethod.GET, path, browser == null ? null : browser.cookie(), null, ifNoneMatch, null);
    }

    private Browser login(String email) {
        Exchange anonymous = exchange(HttpMethod.GET, "/api/v1/csrf", null, null, null, null);
        assertThat(anonymous.status()).isEqualTo(200);
        String csrf = (String) anonymous.body().get("token");
        Exchange signedIn = exchange(HttpMethod.POST, "/api/v1/session", cookie(anonymous), csrf, null,
                Map.of("email", email, "password", PASSWORD));
        assertThat(signedIn.status()).as("sign-in of %s", email).isEqualTo(200);
        return new Browser(cookie(signedIn));
    }

    private static String cookie(Exchange exchange) {
        String cookie = exchange.headers().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie.split(";", 2)[0];
    }

    private Exchange exchange(HttpMethod method, String path, String cookie, String csrf, String ifNoneMatch,
                              Map<String, String> body) {
        var call = RestClient.create().method(method).uri(URI.create("http://localhost:" + port + path));
        if (cookie != null) call.header(HttpHeaders.COOKIE, cookie);
        if (csrf != null) call.header("X-CSRF-TOKEN", csrf);
        if (ifNoneMatch != null) call.header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
        if (body != null) call.contentType(MediaType.APPLICATION_JSON).body(body);
        ResponseEntity<Map<String, Object>> response = call.retrieve().onStatus(status -> true, (req, res) -> {})
                .toEntity(new ParameterizedTypeReference<>() {});
        return new Exchange(response.getStatusCode().value(), response.getHeaders(), response.getBody());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> patient(Exchange exchange) {
        return (Map<String, Object>) exchange.body().get("patient");
    }

    private void ownerExecute(String sql) throws SQLException {
        try (var connection = DemoDatabase.ownerConnection(); var command = connection.createStatement()) {
            command.execute(sql);
        }
    }

    private String ownerValue(String sql) throws SQLException {
        try (var connection = DemoDatabase.ownerConnection(); var command = connection.createStatement();
             var result = command.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    /** Reads through the superuser so that the oracle sees every practice despite forced row-level security. */
    private List<Map<String, Object>> oracleRows(String sql) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (var connection = DemoDatabase.adminConnection(); var command = connection.createStatement();
             var result = command.executeQuery(sql)) {
            var columns = result.getMetaData();
            while (result.next()) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                for (int index = 1; index <= columns.getColumnCount(); index++) {
                    row.put(columns.getColumnLabel(index), result.getString(index));
                }
                rows.add(row);
            }
        }
        return rows;
    }
}
