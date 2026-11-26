package dev.aymen.etaye.security;

import dev.aymen.etaye.AbstractPostgresTest;
import dev.aymen.etaye.TestSessionHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    private int countSpringSessions() {
        try (var conn = java.sql.DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM spring_session")) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hasSessionRow(String sessionId) {
        try (var conn = java.sql.DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
             var ps = conn.prepareStatement("SELECT COUNT(*) FROM spring_session WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void anonymousMetadataIsStatelessAndVersioned() {
        int sessions = countSpringSessions();
        var response = client().get().uri("/api/v1/meta")
                .header("X-Client-Version", "web/0.0.1").retrieve().toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "apiVersion", "v1", "minimumClientVersion", "0.0.1", "latestClientVersion", "0.0.1"));
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(countSpringSessions()).isEqualTo(sessions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/meta", "/api/v1/probes/readiness"})
    void publicReadsDoNotAcceptWritesOrCreateSessions(String path) {
        int initialSessions = countSpringSessions();

        var response = client().post().uri(path)
                .retrieve().onStatus(HttpStatus.METHOD_NOT_ALLOWED::equals, (req, res) -> {}).toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(response.getBody()).containsEntry("code", "METHOD_NOT_ALLOWED");
        assertThat(countSpringSessions()).isEqualTo(initialSessions);
    }

    @Test
    void onlySafeMethodsReachCsrfIssuanceWithoutAToken() {
        UUID account = createAccountAsOwner("csrf.method." + UUID.randomUUID() + "@example.com", "Csrf Method Fixture");
        String cookie = testSessionHelper.createSessionCookie(account);
        var issued = client().get().uri("/api/v1/csrf").header(HttpHeaders.COOKIE, cookie)
                .retrieve().toEntity(Map.class);
        String token = (String) issued.getBody().get("token");

        var denied = client().post().uri("/api/v1/csrf").header(HttpHeaders.COOKIE, cookie)
                .retrieve().onStatus(HttpStatus.FORBIDDEN::equals, (req, res) -> {}).toEntity(Map.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getBody()).containsEntry("code", "CSRF_REJECTED").doesNotContainKey("token");

        var unsupported = client().post().uri("/api/v1/csrf").header(HttpHeaders.COOKIE, cookie)
                .header("X-CSRF-TOKEN", token)
                .retrieve().onStatus(HttpStatus.METHOD_NOT_ALLOWED::equals, (req, res) -> {}).toEntity(Map.class);
        assertThat(unsupported.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(unsupported.getBody()).containsEntry("code", "METHOD_NOT_ALLOWED").doesNotContainKey("token");
    }

    @Test
    void defaultOriginPolicyRejectsPreflightAndActualRequests() {
        String origin = "https://browser.example.test";
        var preflight = client().options().uri("/api/v1/csrf")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .retrieve().onStatus(HttpStatus.FORBIDDEN::equals, (req, res) -> {}).toBodilessEntity();
        var actual = client().get().uri("/api/v1/meta").header(HttpHeaders.ORIGIN, origin)
                .retrieve().onStatus(HttpStatus.FORBIDDEN::equals, (req, res) -> {}).toBodilessEntity();
        assertThat(preflight.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(actual.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(preflight.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        assertThat(actual.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    void sameOriginCookieWorksAndMixedBearerIsRefused() {
        UUID actor = createAccountAsOwner("transport.fixture." + UUID.randomUUID() + "@example.com", "Transport Fixture");
        String cookie = testSessionHelper.createSessionCookie(actor);
        String origin = "http://localhost:" + port;
        var allowed = client().get().uri("/api/v1/csrf").header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.COOKIE, cookie).retrieve().toEntity(Map.class);
        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allowed.getBody()).containsEntry("headerName", "X-CSRF-TOKEN");

        for (String path : new String[]{"/api/v1/csrf", "/api/v1/meta", "/api/v1/probes/readiness"}) {
            var rejected = client().get().uri(path).header(HttpHeaders.COOKIE, cookie)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-fixture")
                    .retrieve().onStatus(HttpStatus.FORBIDDEN::equals, (req, res) -> {}).toEntity(Map.class);
            assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(rejected.getHeaders().getContentType())
                    .matches(MediaType.APPLICATION_PROBLEM_JSON::includes);
            assertThat(rejected.getBody()).containsEntry("code", "FORBIDDEN");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/probes/readiness", "/api/v1/meta", "/api/v1/csrf"})
    void unsupportedPublicResponseFormatReturnsDocumentedProblem(String path) {
        var response = client().get().uri(path).accept(MediaType.TEXT_PLAIN)
                .retrieve().onStatus(HttpStatus.NOT_ACCEPTABLE::equals, (req, res) -> {}).toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).containsEntry("code", "NOT_ACCEPTABLE");
    }

    @Test
    @DisplayName("A2: Unauthenticated protected request gives 401, creates NO row in spring_session, and sends no Set-Cookie")
    void unauthenticatedRequestCreatesNoSession() {
        int initialSessions = countSpringSessions();

        ResponseEntity<Map> response = client().get()
                .uri("/api/v1/practices/" + UUID.randomUUID() + "/records/" + UUID.randomUUID())
                .retrieve()
                .onStatus(status -> status.value() == 401, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(countSpringSessions()).isEqualTo(initialSessions);
    }

    @Test
    @DisplayName("A2: Authenticated POST /logout is not served as a logout (404/405 problem) and session still exists afterwards")
    void logoutIsNotServed() {
        UUID accountId = createAccountAsOwner("logout.test." + UUID.randomUUID() + "@example.com", "Logout Test");
        String sessionCookie = testSessionHelper.createSessionCookie(accountId);

        ResponseEntity<Map> csrfResponse = client().get()
                .uri("/api/v1/csrf")
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve()
                .toEntity(Map.class);
        String csrfToken = (String) csrfResponse.getBody().get("token");

        int initialSessions = countSpringSessions();

        ResponseEntity<Map> response = client().post()
                .uri("/logout")
                .header(HttpHeaders.COOKIE, sessionCookie)
                .header("X-CSRF-TOKEN", csrfToken)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode().value()).isIn(404, 405);
        assertThat(countSpringSessions()).isEqualTo(initialSessions);
    }

    @Test
    @DisplayName("A4: A suspended account's old cookie on /csrf gives 200 with token and new session id, and old session is gone")
    void suspendedAccountCookieOnCsrfIssuesNewSessionAndDeletesOldSession() {
        String email = "csrf.suspended." + UUID.randomUUID() + "@example.com";
        UUID accountId = createAccountAsOwner(email, "CSRF Suspended User");
        UUID practiceId = createPracticeAsOwner("Cabinet CSRF Suspended");
        createMembershipAsOwner(accountId, practiceId, "ACTIVE", new String[]{"CLINICAL_READ"});

        // 1. Create a valid session
        String oldSessionCookie = testSessionHelper.createSessionCookie(accountId);
        String rawOldCookie = oldSessionCookie.replace("__Host-etaye-session=", "").split(";")[0];
        String oldSessionId = new String(java.util.Base64.getDecoder().decode(rawOldCookie), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(hasSessionRow(oldSessionId)).isTrue();

        // 2. Suspend account in database
        try (var conn = java.sql.DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
             var ps = conn.prepareStatement("UPDATE account SET state = 'SUSPENDED' WHERE id = ?")) {
            ps.setObject(1, accountId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 3. Request /api/v1/csrf using the suspended account's cookie
        ResponseEntity<Map> csrfResponse = client().get()
                .uri("/api/v1/csrf")
                .header(HttpHeaders.COOKIE, oldSessionCookie)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
                .toEntity(Map.class);

        // Must be 200 OK
        assertThat(csrfResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csrfResponse.getBody()).isNotNull();
        assertThat(csrfResponse.getBody().get("headerName")).isEqualTo("X-CSRF-TOKEN");
        assertThat(csrfResponse.getBody().get("token")).isNotNull();

        // Must send a NEW Set-Cookie
        String newSetCookie = csrfResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(newSetCookie).isNotNull();
        assertThat(newSetCookie).contains("__Host-etaye-session=");
        assertThat(oldSessionCookie).doesNotContain(newSetCookie.split(";")[0]);

        // Old session row must be physically gone from the database
        assertThat(hasSessionRow(oldSessionId)).isFalse();

        // 4. Using the same old cookie on a protected route still gives 401
        ResponseEntity<Map> protectedResponse = client().get()
                .uri("/api/v1/practices/" + practiceId + "/records/" + UUID.randomUUID())
                .header(HttpHeaders.COOKIE, oldSessionCookie)
                .retrieve()
                .onStatus(status -> status.value() == 401, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(protectedResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 5. The replacement session is anonymous: even after the account is reactivated it carries no authentication
        try (var conn = java.sql.DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
             var ps = conn.prepareStatement("UPDATE account SET state = 'ACTIVE' WHERE id = ?")) {
            ps.setObject(1, accountId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String replacementCookie = newSetCookie.split(";")[0];
        ResponseEntity<Map> replacementResponse = client().get()
                .uri("/api/v1/practices/" + practiceId + "/records/" + UUID.randomUUID())
                .header(HttpHeaders.COOKIE, replacementCookie)
                .retrieve()
                .onStatus(status -> status.value() == 401, (req, res) -> {})
                .toEntity(Map.class);
        assertThat(replacementResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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
    @DisplayName("B7(a): Non-secure MockHttpServletRequest still emits Secure cookie attribute via explicit serializer configuration")
    void nonSecureRequestStillEmitsSecureCookie() {
        assertThat(cookieSerializer).isInstanceOf(DefaultCookieSerializer.class);

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setSecure(false);
        request.setScheme("http");
        request.setServerPort(80);
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();

        String sessionId = UUID.randomUUID().toString();
        cookieSerializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, sessionId));

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();

        java.util.List<String> parts = java.util.Arrays.asList(setCookie.split("; "));
        String expectedValue = java.util.Base64.getEncoder()
                .encodeToString(sessionId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(parts.get(0)).isEqualTo("__Host-etaye-session=" + expectedValue);
        assertThat(parts.subList(1, parts.size()))
                .containsExactlyInAnyOrder("Path=/", "Secure", "HttpOnly", "SameSite=Lax");
    }

    @Test
    @DisplayName("B7(b): Real HTTP anonymous GET /api/v1/csrf returns real Set-Cookie with __Host-etaye-session and exact hardened attributes")
    void realHttpAnonymousCsrfReturnsExactHardenedSessionCookie() {
        ResponseEntity<Map> response = client().get()
                .uri("/api/v1/csrf")
                .retrieve()
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).as("Set-Cookie header must be present on anonymous /csrf").isNotNull();

        java.util.List<String> parts = java.util.Arrays.asList(setCookie.split("; "));
        String nameAndValue = parts.get(0);
        assertThat(nameAndValue).startsWith("__Host-etaye-session=");

        String base64Value = nameAndValue.substring("__Host-etaye-session=".length());
        byte[] decoded = java.util.Base64.getDecoder().decode(base64Value);
        String rawSessionId = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(rawSessionId).isNotBlank();

        // Exact attributes: Path=/, Secure, HttpOnly, SameSite=Lax (no Domain, no Max-Age, no Expires)
        assertThat(parts.subList(1, parts.size()))
                .containsExactlyInAnyOrder("Path=/", "Secure", "HttpOnly", "SameSite=Lax");

        // Explicitly assert absence of forbidden attributes
        for (String part : parts) {
            String lower = part.toLowerCase();
            assertThat(lower).doesNotStartWith("domain=");
            assertThat(lower).doesNotStartWith("max-age=");
            assertThat(lower).doesNotStartWith("expires=");
        }
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
