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
    @DisplayName("Security: getCsrfToken returns 200 with X-CSRF-TOKEN headerName and no JavaScript-readable cookie")
    void getCsrfTokenReturnsTokenAndNoCookie() {
        UUID accountId = createAccountAsOwner("csrf.test." + UUID.randomUUID() + "@example.com", "CSRF Test");
        String sessionCookie = testSessionHelper.createSessionCookie(accountId);

        ResponseEntity<Map> response = client().get()
                .uri("/api/v1/csrf")
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve()
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("headerName")).isEqualTo("X-CSRF-TOKEN");
        assertThat(body.get("token")).isNotNull();

        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        if (setCookie != null) {
            assertThat(setCookie).doesNotContain("XSRF-TOKEN");
        }
    }

    @Test
    @DisplayName("C-6: Session cookie serializer enforces __Host-etaye-session, HttpOnly, Secure, SameSite=Lax, Path=/, no Domain")
    void sessionCookieSerializerInvariants() {
        assertThat(cookieSerializer).isInstanceOf(DefaultCookieSerializer.class);

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setSecure(true);
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();

        String sessionId = UUID.randomUUID().toString();
        cookieSerializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, sessionId));

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();

        // The serializer Base64-encodes the session id; the attributes must be exactly the hardened set
        // (no Domain so the __Host- prefix is honoured, no Max-Age so it stays a browser-session cookie).
        java.util.List<String> parts = java.util.Arrays.asList(setCookie.split("; "));
        String expectedValue = java.util.Base64.getEncoder()
                .encodeToString(sessionId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(parts.get(0)).isEqualTo("__Host-etaye-session=" + expectedValue);
        assertThat(parts.subList(1, parts.size()))
                .containsExactlyInAnyOrder("Path=/", "Secure", "HttpOnly", "SameSite=Lax");
    }

    @Test
    @DisplayName("B6: Suspending account deletes session; request denied with 401; reactivating account with same cookie still denied")
    void suspendedAccountInvalidatesSessionAndDeniesAccessEvenIfReactivated() {
        String email = "suspended." + UUID.randomUUID() + "@example.com";
        UUID accountId = createAccountAsOwner(email, "Suspended User");
        UUID practiceId = createPracticeAsOwner("Cabinet Suspendu");
        createMembershipAsOwner(accountId, practiceId, "ACTIVE", new String[]{"CLINICAL_READ"});

        // Create valid session
        String sessionCookie = testSessionHelper.createSessionCookie(accountId);

        // 1. Suspend the account in database
        try (var conn = java.sql.DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
             var ps = conn.prepareStatement("UPDATE account SET state = 'SUSPENDED' WHERE id = ?")) {
            ps.setObject(1, accountId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 2. Request using existing session cookie must be rejected with 401 and session deleted
        ResponseEntity<Map> response1 = client().get()
                .uri("/api/v1/practices/" + practiceId + "/records/" + UUID.randomUUID())
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve()
                .onStatus(status -> status.value() == 401, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response1.getHeaders().getContentType().toString()).contains("application/problem+json");
        Map<String, Object> body1 = response1.getBody();
        assertThat(body1).isNotNull();
        assertThat(body1.get("status")).isEqualTo(401);
        assertThat(body1.get("code")).isEqualTo("UNAUTHORIZED");
        assertThat(body1.get("correlationId")).isNotNull();

        // 3. Reactivate the account in database
        try (var conn = java.sql.DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
             var ps = conn.prepareStatement("UPDATE account SET state = 'ACTIVE' WHERE id = ?")) {
            ps.setObject(1, accountId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 4. Request with SAME cookie must STILL be denied with 401 because session was physically deleted
        ResponseEntity<Map> response2 = client().get()
                .uri("/api/v1/practices/" + practiceId + "/records/" + UUID.randomUUID())
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve()
                .onStatus(status -> status.value() == 401, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response2.getBody()).isNotNull();
        assertThat(response2.getBody().get("status")).isEqualTo(401);
        assertThat(response2.getBody().get("code")).isEqualTo("UNAUTHORIZED");
    }
}
