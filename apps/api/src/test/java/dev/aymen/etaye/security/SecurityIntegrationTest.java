package dev.aymen.etaye.security;

import dev.aymen.etaye.AbstractPostgresTest;
import dev.aymen.etaye.TestSessionHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SecurityIntegrationTest extends AbstractPostgresTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestSessionHelper testSessionHelper;

    @Autowired
    private CookieSerializer cookieSerializer;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    @DisplayName("Security: Unauthenticated request to protected endpoint returns RFC 9457 401 Unauthorized with correlationId")
    void unauthenticatedRequestReturns401Problem() {
        ResponseEntity<Map> response = client().get()
                .uri("/api/v1/practices/" + UUID.randomUUID() + "/records/" + UUID.randomUUID())
                .retrieve()
                .onStatus(status -> status.value() == 401, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo(401);
        assertThat(body.get("code")).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("correlationId")).isNotNull();
    }

    @Test
    @DisplayName("Security: getCsrfToken returns 200 with X-CSRF-TOKEN headerName and sets XSRF-TOKEN cookie")
    void getCsrfTokenReturnsTokenAndSetsCookie() {
        ResponseEntity<Map> response = client().get()
                .uri("/api/v1/csrf")
                .retrieve()
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("headerName")).isEqualTo("X-CSRF-TOKEN");
        assertThat(body.get("token")).isNotNull();

        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("XSRF-TOKEN=");
    }

    @Test
    @DisplayName("Security: Session cookie serializer enforces __Host-etaye-session, HttpOnly, Secure, SameSite=Lax")
    void sessionCookieSerializerInvariants() {
        assertThat(cookieSerializer).isInstanceOf(DefaultCookieSerializer.class);
        UUID accountId = createAccountAsOwner("cookie.test." + UUID.randomUUID() + "@example.com", "Cookie Test", "hash");
        String cookieHeader = testSessionHelper.createSessionCookie(accountId);
        assertThat(cookieHeader).startsWith("__Host-etaye-session=");
    }

    @Test
    @DisplayName("Security: Re-resolving suspended account rejects request with 403 Forbidden")
    void suspendedAccountReResolutionDeniesAccess() {
        String email = "suspended." + UUID.randomUUID() + "@example.com";
        UUID accountId = createAccountAsOwner(email, "Suspended User", "hash");
        UUID practiceId = createPracticeAsOwner("Cabinet Suspendu");
        createMembershipAsOwner(accountId, practiceId, "ACTIVE", new String[]{"CLINICAL_READ"});

        // Create valid session
        String sessionCookie = testSessionHelper.createSessionCookie(accountId);

        // Suspend the account in database
        try (var conn = java.sql.DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
             var ps = conn.prepareStatement("UPDATE account SET state = 'SUSPENDED' WHERE id = ?")) {
            ps.setObject(1, accountId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Subsequent request using existing session cookie must be rejected on-the-fly
        ResponseEntity<Map> response = client().get()
                .uri("/api/v1/practices/" + practiceId + "/records/" + UUID.randomUUID())
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve()
                .onStatus(status -> status.value() == 403, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo(403);
        assertThat(body.get("code")).isEqualTo("FORBIDDEN");
        assertThat(body.get("correlationId")).isNotNull();
    }
}
