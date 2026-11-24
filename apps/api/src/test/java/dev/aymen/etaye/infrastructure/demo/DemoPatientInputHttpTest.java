package dev.aymen.etaye.infrastructure.demo;

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
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The local demonstration package opts in to patient input (story 3.1): the fictional practitioner
 * and administrator register and correct patients through the real operations, still bound by their
 * own grants. The default, covered by DemoHttpTest, keeps every input surface closed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"etaye.demo.enabled=true", "etaye.demo.patient-input-enabled=true"})
@ActiveProfiles("test")
class DemoPatientInputHttpTest {

    private static final String PASSWORD = "Etaye-Jeu-Fictif-91!";

    @DynamicPropertySource
    static void demoDatabase(DynamicPropertyRegistry registry) {
        DemoDatabase.configureDemo(registry);
    }

    @LocalServerPort private int port;

    private record Browser(String cookie, String csrf) {}

    @Test
    void optedInDemoRegistersAndCorrectsThroughTheRealGrants() throws SQLException {
        Browser nora = login("nora.lefebvre@example.test");
        Browser mila = login("mila.dubois@example.test");
        String practice = ownerValue("SELECT etaye_demo.fixture_id('PRA-A')");
        String patients = "/practices/" + practice + "/patients";

        var created = request(HttpMethod.POST, patients, nora, Map.of("Idempotency-Key", UUID.randomUUID().toString()),
                Map.of("firstName", "Camille", "lastName", "Fictive", "dateOfBirth", "1990-01-01"));
        assertThat(created.getStatusCode().value()).isEqualTo(201);

        var corrected = request(HttpMethod.PATCH, patients + "/" + created.getBody().get("id"), nora, Map.of("If-Match", "\"0\""),
                Map.of("firstName", "Camille-Anne", "lastName", "Fictive", "dateOfBirth", "1990-01-01"));
        assertThat(corrected.getStatusCode().value()).isEqualTo(200);

        // Grants still decide: Mila registers for scheduling but has no clinical route.
        var registered = request(HttpMethod.POST, "/practices/" + practice + "/scheduling/patients", mila,
                Map.of("Idempotency-Key", UUID.randomUUID().toString()),
                Map.of("firstName", "Hélène", "lastName", "Fictif", "dateOfBirth", "1988-02-02"));
        assertThat(registered.getStatusCode().value()).isEqualTo(201);
        assertThat(request(HttpMethod.GET, patients, mila, Map.of(), null).getStatusCode().value()).isEqualTo(403);
    }

    private Browser login(String email) {
        var token = request(HttpMethod.GET, "/csrf", null, Map.of(), null);
        Browser anonymous = new Browser(cookie(token), token.getBody().get("token").toString());
        var session = request(HttpMethod.POST, "/session", anonymous, Map.of(), Map.of("email", email, "password", PASSWORD));
        assertThat(session.getStatusCode().value()).isEqualTo(200);
        String cookie = cookie(session);
        var fresh = request(HttpMethod.GET, "/csrf", new Browser(cookie, null), Map.of(), null);
        return new Browser(cookie, fresh.getBody().get("token").toString());
    }

    private String cookie(ResponseEntity<?> response) {
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie.split(";", 2)[0];
    }

    private ResponseEntity<Map<String, Object>> request(HttpMethod method, String path, Browser browser,
                                                         Map<String, String> headers, Map<String, ?> body) {
        var call = RestClient.create().method(method).uri(URI.create("http://localhost:" + port + "/api/v1" + path));
        if (browser != null) {
            call.header(HttpHeaders.COOKIE, browser.cookie());
            if (browser.csrf() != null) call.header("X-CSRF-TOKEN", browser.csrf());
        }
        headers.forEach(call::header);
        if (body != null) call.contentType(MediaType.APPLICATION_JSON).body(body);
        return call.retrieve().onStatus(status -> true, (request, response) -> {})
                .toEntity(new ParameterizedTypeReference<>() {});
    }

    private String ownerValue(String sql) throws SQLException {
        try (var connection = DemoDatabase.ownerConnection();
             var command = connection.createStatement(); var result = command.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
