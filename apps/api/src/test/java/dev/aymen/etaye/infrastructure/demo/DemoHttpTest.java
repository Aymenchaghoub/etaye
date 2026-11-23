package dev.aymen.etaye.infrastructure.demo;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "etaye.demo.enabled=true")
@ActiveProfiles("test")
class DemoHttpTest {

    private static final String PASSWORD = "Etaye-Jeu-Fictif-91!";
    private static final String OPERATOR = "operateur@example.test";
    private static final String RESET_PATH = "/demo/reset";
    private static final String REASON = "Restaurer le jeu fictif";

    @DynamicPropertySource
    static void demoDatabase(DynamicPropertyRegistry registry) {
        DemoDatabase.configureDemo(registry);
    }

    @LocalServerPort private int port;
    @Autowired private DemoFixture fixture;
    @Autowired private LoginPause loginPause;

    @TestConfiguration
    static class PauseConfiguration {
        @Bean
        LoginPause loginPause() {
            return new LoginPause(new CountDownLatch(1), new CountDownLatch(1));
        }

        @Bean
        FilterRegistrationBean<Filter> pauseInFlightLogin(LoginPause pause) {
            Filter filter = (request, response, chain) -> {
                if ("true".equals(((HttpServletRequest) request).getHeader("X-Test-Hold-Login"))) {
                    pause.entered().countDown();
                    try {
                        if (!pause.release().await(4, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Test did not release the in-flight login");
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                }
                chain.doFilter(request, response);
            };
            var registration = new FilterRegistrationBean<>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
            registration.addUrlPatterns("/api/v1/session");
            return registration;
        }
    }

    private record LoginPause(CountDownLatch entered, CountDownLatch release) {}

    @Test
    void catalogueIsPublicStatelessAndPaginated() {
        var first = request(HttpMethod.GET, "/demo/scenarios?limit=1", null, null, null);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(first.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(first.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(first.getBody()).containsKey("nextCursor");
        assertThat(first.getBody().get("items").toString()).contains("Dr Nora Lefebvre", "Cabinet des Tilleuls",
                "Données fictives", "nora.lefebvre@example.test");
        var next = request(HttpMethod.GET, "/demo/scenarios?cursor=" + first.getBody().get("nextCursor"), null, null, null);
        assertThat(next.getBody()).containsEntry("nextCursor", null);
        assertThat(next.getBody().get("items").toString()).contains("Claire Martin", "Mila Dubois");
        assertThat(request(HttpMethod.GET, "/demo/scenarios?cursor=invalid", null, null, null).getStatusCode().value())
                .isEqualTo(400);
        assertThat(request(HttpMethod.GET, "/demo/scenarios?limit=101", null, null, null).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void resetOnlyRestoresTheDedicatedFixtureAndClosesExistingSessions() throws Exception {
        var nora = login("nora.lefebvre@example.test");
        var mila = login("mila.dubois@example.test");
        var operator = login(OPERATOR);
        var anotherOperator = login(OPERATOR);
        var before = fixture.version();
        var key = UUID.randomUUID().toString();
        String practice = ownerValue("SELECT etaye_demo.fixture_id('PRA-A')");
        String other = ownerValue("SELECT etaye_demo.fixture_id('PRA-B')");
        String claire = ownerValue("SELECT etaye_demo.fixture_id('R-A-001')");
        String hugo = ownerValue("SELECT etaye_demo.fixture_id('R-B-001')");
        var allowed = "/practices/" + practice + "/records/" + claire;

        assertThat(request(HttpMethod.GET, allowed, nora, null, null).getStatusCode().value()).isEqualTo(200);
        assertThat(request(HttpMethod.GET, "/practices/" + other + "/records/" + hugo, nora, null, null)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(request(HttpMethod.GET, allowed, mila, null, null).getStatusCode().value()).isEqualTo(403);
        assertThat(request(HttpMethod.GET, "/session", operator, null, null).getBody())
                .containsEntry("contexts", List.of());
        var patient = Map.of("firstName", "Camille", "lastName", "Fictive", "dateOfBirth", "1990-01-01");
        var create = "/practices/" + practice + "/patients";
        assertThat(request(HttpMethod.POST, create, csrf(nora),
                key, patient).getStatusCode().value()).isEqualTo(403);

        assertThat(request(HttpMethod.POST, create, csrf(mila), key, patient).getStatusCode().value()).isEqualTo(403);
        // The other story 3.1 input surfaces stay closed in the public demo unless the local package opts in.
        assertThat(request(HttpMethod.PATCH, create + "/" + UUID.randomUUID(), csrf(nora), null, patient)
                .getStatusCode().value()).isEqualTo(403);
        assertThat(request(HttpMethod.POST, "/practices/" + practice + "/scheduling/patients", csrf(mila), key, patient)
                .getStatusCode().value()).isEqualTo(403);
        assertThat(reset(null, key, REASON).getStatusCode().value()).isEqualTo(401);
        assertThat(request(HttpMethod.POST, "/demo/%72eset", null, key, Map.of("reason", REASON))
                .getStatusCode().value()).isEqualTo(401);
        var noraCsrf = csrf(nora);
        assertThat(reset(noraCsrf, key, REASON).getStatusCode().value()).isEqualTo(403);
        assertThat(request(HttpMethod.POST, RESET_PATH, noraCsrf, null, Map.of()).getStatusCode().value()).isEqualTo(403);
        assertThat(reset(new Browser(operator.cookie(), null), key, REASON).getStatusCode().value()).isEqualTo(403);
        assertThat(reset(csrf(operator), key, " ").getStatusCode().value()).isBetween(400, 422);
        assertThat(fixture.version()).isEqualTo(before);
        var portal = request(HttpMethod.POST, "/invitations/accept", csrf(null), UUID.randomUUID().toString(),
                Map.of("token", "etaye-fictional-invitation-INV-PORTAL-03",
                        "recipientVerification", "1990-03-12", "displayName", "Marie Collet", "password", PASSWORD));
        assertThat(portal.getStatusCode().value()).isEqualTo(200);
        assertThat(portal.getBody()).containsKey("activatedPortalLinkId");

        ownerExecute("UPDATE public.practice SET display_name = 'Cabinet fictif modifié' WHERE id = '" + practice + "'");
        ownerExecute("""
                BEGIN;
                SELECT set_config('etaye.practice_id', etaye_demo.fixture_id('PRA-A')::text, true);
                UPDATE public.invitation SET state = 'REVOKED' WHERE id = etaye_demo.fixture_id('INV-TEAM-01');
                COMMIT;
                """);
        var operatorCsrf = csrf(operator);
        var otherCsrf = csrf(anotherOperator);
        var lateLoginCsrf = csrf(null);
        ResponseEntity<Map<String, Object>> reset;
        String lateCookie;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var lateLogin = executor.submit(() -> requestAt(HttpMethod.POST, "/api/%761/session", lateLoginCsrf, null,
                    Map.of("email", "claire.martin@example.test", "password", PASSWORD), true));
            java.util.concurrent.Future<ResponseEntity<Map<String, Object>>> first;
            try {
                assertThat(loginPause.entered().await(3, TimeUnit.SECONDS)).isTrue();
                first = executor.submit(() -> requestAt(HttpMethod.POST, "/api/v1/demo/%72eset", operatorCsrf, key,
                        Map.of("reason", REASON), false));
                var waiting = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
                while (request(HttpMethod.GET, "/meta", null, null, null).getStatusCode().value() == 200
                        && System.nanoTime() < waiting) {
                    Thread.sleep(10);
                }
                var meta = request(HttpMethod.GET, "/meta", null, null, null);
                assertThat(meta.getStatusCode().value()).isEqualTo(503);
                assertThat(meta.getBody()).containsEntry("code", "DEMO_RESET_IN_PROGRESS");
                var concurrent = reset(otherCsrf, UUID.randomUUID().toString(), REASON);
                assertThat(concurrent.getStatusCode().value()).isEqualTo(409);
                assertThat(concurrent.getBody()).containsEntry("code", "DEMO_RESET_IN_PROGRESS");
            } finally {
                loginPause.release().countDown();
            }
            var login = lateLogin.get(5, TimeUnit.SECONDS);
            assertThat(login.getStatusCode().value()).isEqualTo(200);
            lateCookie = cookie(login);
            reset = first.get(5, TimeUnit.SECONDS);
        }

        assertThat(reset.getStatusCode().value()).isEqualTo(202);
        assertThat(reset.getBody()).containsEntry("accepted", true);
        assertThat(fixture.version()).isEqualTo(new DemoFixture.Version(before.version(), before.generation() + 1));
        assertThat(request(HttpMethod.GET, "/meta", null, null, null).getBody())
                .containsEntry("demoDatasetVersion", "etaye-design-v1");
        assertThat(ownerValue("SELECT display_name FROM public.practice WHERE id = '" + practice + "'"))
                .isEqualTo("Cabinet des Tilleuls");
        try (var connection = DemoDatabase.ownerConnection();
             var command = connection.createStatement()) {
            connection.setAutoCommit(false);
            command.execute("SELECT set_config('etaye.practice_id', etaye_demo.fixture_id('PRA-A')::text, true)");
            try (var row = command.executeQuery(
                    "SELECT state FROM public.invitation WHERE id = etaye_demo.fixture_id('INV-TEAM-01')")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("PENDING");
            }
            try (var row = command.executeQuery("""
                    SELECT count(*) FROM public.invitation WHERE
                        (id = etaye_demo.fixture_id('INV-PORTAL-01') AND created_at < accepted_at
                            AND updated_at = accepted_at AND accepted_at < expires_at)
                        OR (id = etaye_demo.fixture_id('INV-PORTAL-02') AND created_at < expires_at
                            AND updated_at = expires_at)
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getInt(1)).isEqualTo(2);
            }
        }
        assertThat(request(HttpMethod.GET, "/session", nora, null, null).getStatusCode().value()).isEqualTo(401);
        assertThat(request(HttpMethod.GET, "/session", operator, null, null).getStatusCode().value()).isEqualTo(401);
        assertThat(request(HttpMethod.GET, "/session", new Browser(lateCookie, null), null, null)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(request(HttpMethod.POST, "/access-recovery", operatorCsrf, key,
                Map.of("email", OPERATOR)).getStatusCode().value()).isIn(401, 403);

        var newOperator = csrf(login(OPERATOR));
        var replay = reset(newOperator, key, REASON);
        assertThat(replay.getStatusCode().value()).isEqualTo(202);
        assertThat(replay.getBody()).isEqualTo(reset.getBody());
        var conflict = reset(newOperator, key, "Autre motif fictif");
        assertThat(conflict.getStatusCode().value()).isEqualTo(409);
        assertThat(conflict.getBody()).containsEntry("code", "IDEMPOTENCY_CONFLICT");
        var limited = reset(newOperator, UUID.randomUUID().toString(), REASON);
        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(Integer.parseInt(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isBetween(1, 30);
        assertThat(fixture.version().generation()).isEqualTo(before.generation() + 1);
    }

    @Test
    void ordinaryDatabaseCannotInstallDemoReset() {
        // Exercise the SQL guard even on ordinary databases past the demo-only versions.
        assertThatThrownBy(() -> DemoDatabase.flyway(DemoDatabase.ordinaryUrl(), true).migrate())
                .hasStackTraceContaining("The synthetic fixture requires its dedicated PostgreSQL database");
    }

    private ResponseEntity<Map<String, Object>> reset(Browser browser, String key, String reason) {
        return request(HttpMethod.POST, RESET_PATH, browser, key, Map.of("reason", reason));
    }

    private Browser login(String email) {
        var response = request(HttpMethod.POST, "/session", csrf(null), null, Map.of("email", email, "password", PASSWORD));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return new Browser(cookie(response), null);
    }

    private Browser csrf(Browser browser) {
        var response = request(HttpMethod.GET, "/csrf", browser, null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return new Browser(browser == null ? cookie(response) : browser.cookie(), response.getBody().get("token").toString());
    }

    private String cookie(ResponseEntity<?> response) {
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie.split(";", 2)[0];
    }

    private ResponseEntity<Map<String, Object>> request(HttpMethod method, String path, Browser browser, String key,
                                                         Map<String, String> body) {
        return requestAt(method, "/api/v1" + path, browser, key, body, false);
    }

    private ResponseEntity<Map<String, Object>> requestAt(HttpMethod method, String path, Browser browser, String key,
                                                           Map<String, String> body, boolean pauseLogin) {
        var call = RestClient.create().method(method).uri(URI.create("http://localhost:" + port + path));
        if (pauseLogin) call.header("X-Test-Hold-Login", "true");
        if (browser != null) {
            call.header(HttpHeaders.COOKIE, browser.cookie());
            if (browser.csrf() != null) call.header("X-CSRF-TOKEN", browser.csrf());
        }
        if (key != null) call.header("Idempotency-Key", key);
        if (body != null) call.contentType(MediaType.APPLICATION_JSON).body(body);
        return call.retrieve().onStatus(status -> true, (request, response) -> {})
                .toEntity(new ParameterizedTypeReference<>() {});
    }

    private void ownerExecute(String sql) throws SQLException {
        try (var connection = DemoDatabase.ownerConnection();
             var command = connection.createStatement()) {
            command.execute(sql);
        }
    }

    private String ownerValue(String sql) throws SQLException {
        try (var connection = DemoDatabase.ownerConnection();
             var command = connection.createStatement(); var result = command.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    record Browser(String cookie, String csrf) {}
}
