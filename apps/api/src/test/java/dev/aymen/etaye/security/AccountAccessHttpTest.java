package dev.aymen.etaye.security;

import dev.aymen.etaye.AbstractPostgresTest;
import dev.aymen.etaye.MutableTestClock;
import dev.aymen.etaye.TestSessionHelper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "etaye.web.session-idle=PT20S", "etaye.web.session-absolute=PT40S"
})
class AccountAccessHttpTest extends AbstractPostgresTest {

    private static final String OLD_PASSWORD = "Fictional-password-" + UUID.randomUUID();
    private static final Pbkdf2PasswordEncoder ENCODER = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @LocalServerPort private int port;
    @Autowired private MutableTestClock clock;
    @Autowired private TestSessionHelper legacySessions;

    @TestConfiguration
    static class TimeConfiguration {
        @Bean
        MutableTestClock accountAccessTestClock() {
            return new MutableTestClock(Instant.now());
        }
    }

    @Test
    void signInRotatesCookieAndCsrfAndReturnsOnlyCurrentGrants() {
        String email = syntheticEmail();
        UUID account = account(email);
        UUID practice = createPracticeAsOwner("Cabinet fictif");
        UUID membership = createMembershipAsOwner(account, practice, "ACTIVE", new String[]{"CLINICAL_READ"});
        createMembershipAsOwner(account, createPracticeAsOwner("Cabinet suspendu"), "SUSPENDED", new String[]{"CLINICAL_READ"});
        UUID record = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID portal = UUID.randomUUID();
        executeAsOwner("""
                BEGIN;
                SELECT set_config('etaye.practice_id', '%s', true);
                INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth)
                    VALUES ('%s', '%s', 'Camille', 'Exemple', '1980-01-01');
                INSERT INTO patient_record (id, practice_id, patient_id) VALUES ('%s', '%s', '%s');
                INSERT INTO portal_link (id, account_id, practice_id, record_id, verified_by, state)
                    VALUES ('%s', '%s', '%s', '%s', '%s', 'ACTIVE');
                COMMIT;
                """.formatted(practice, patient, practice, record, practice, patient, portal, account, practice, record, account));

        var anonymous = csrf(null);
        var missing = request(HttpMethod.POST, "/session", anonymous.cookie(), null,
                Map.of("email", email, "password", OLD_PASSWORD), null);
        assertThat(missing.getStatusCode().value()).isEqualTo(403);
        var wrong = signIn(new Browser(anonymous.cookie(), "incorrect"), email, OLD_PASSWORD, "/compte");
        assertThat(wrong.getStatusCode().value()).isEqualTo(403);

        String target = "/compte/pratiques/" + membership;
        var login = signIn(anonymous, email.toUpperCase(java.util.Locale.ROOT), OLD_PASSWORD, target);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        assertThat(login.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(login.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("Secure", "HttpOnly", "SameSite=Lax", "Path=/");
        assertThat(login.getBody())
                .containsEntry("membershipIds", List.of(membership.toString()))
                .containsEntry("portalLinkIds", List.of(portal.toString()))
                .containsEntry("returnTo", target)
                .doesNotContainKeys("csrf", "password", "token");
        var cookie = cookie(login);
        assertThat(cookie.equals(anonymous.cookie())).isFalse();
        assertThat(request(HttpMethod.GET, "/session", anonymous.cookie(), null, null, null).getStatusCode().value())
                .isEqualTo(401);
        var rotated = csrf(cookie);
        assertThat(rotated.csrf().equals(anonymous.csrf())).isFalse();
        assertThat(request(HttpMethod.DELETE, "/session", cookie, anonymous.csrf(), null, null).getStatusCode().value())
                .isEqualTo(403);

        String recordPath = "/practices/" + practice + "/records/" + record;
        assertThat(request(HttpMethod.GET, recordPath, cookie, null, null, null).getStatusCode().value()).isEqualTo(200);
        executeAsOwner("UPDATE practice SET state = 'SUSPENDED' WHERE id = '" + practice + "'");
        assertThat(request(HttpMethod.GET, recordPath, cookie, null, null, null).getStatusCode().value()).isEqualTo(403);
        executeAsOwner("UPDATE practice SET state = 'ACTIVE' WHERE id = '" + practice + "'");
        updateMembershipStateAsOwner(account, practice, "SUSPENDED");
        executeAsOwner("UPDATE portal_link SET state = 'REVOKED' WHERE id = '" + portal + "'");
        var current = request(HttpMethod.GET, "/session", cookie, null, null, null);
        assertThat(current.getBody()).containsEntry("membershipIds", List.of()).containsEntry("portalLinkIds", List.of())
                .doesNotContainKey("returnTo");

        var external = signIn(rotated, email, OLD_PASSWORD, "//untrusted.example.test");
        assertThat(external.getBody()).containsEntry("returnTo", "/compte");
        var currentBrowser = csrf(cookie(external));
        var denied = signIn(currentBrowser, email, OLD_PASSWORD, target);
        assertThat(denied.getBody()).containsEntry("returnTo", "/compte");
        var signedInCookie = cookie(denied);
        var signedInBrowser = csrf(signedInCookie);
        assertThat(request(HttpMethod.DELETE, "/session", signedInCookie, signedInBrowser.csrf(), null, null)
                .getStatusCode().value()).isEqualTo(204);
        assertThat(request(HttpMethod.GET, "/session", signedInCookie, null, null, null).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void credentialFailuresLookTheSameAndAreBounded() {
        String active = syntheticEmail();
        account(active);
        String suspended = syntheticEmail();
        updateAccountStateAsOwner(account(suspended), "SUSPENDED");
        var browser = csrf(null);
        var bad = signIn(browser, active, "wrong", null);
        var unknown = signIn(browser, syntheticEmail(), OLD_PASSWORD, null);
        var inactive = signIn(browser, suspended, OLD_PASSWORD, null);
        assertThat(List.of(bad, unknown, inactive)).allSatisfy(response -> {
            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(response.getBody()).containsEntry("code", "INVALID_CREDENTIALS")
                    .containsEntry("detail", bad.getBody().get("detail"));
        });
        for (int attempt = 0; attempt < 7; attempt++) {
            assertThat(signIn(browser, active, "wrong", null).getStatusCode().value()).isEqualTo(401);
        }
        var limited = signIn(browser, active, "wrong", null);
        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(Integer.parseInt(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isPositive();
    }

    @Test
    void inspectionCannotExtendIdleAndActivityCannotExtendAbsoluteDeadline() {
        String idleEmail = syntheticEmail();
        account(idleEmail);
        var idle = signIn(csrf(null), idleEmail, OLD_PASSWORD, null);
        var idleCookie = cookie(idle);
        Instant idleExpiry = Instant.parse((String) idle.getBody().get("expiresAt"));
        clock.advanceSeconds(12);
        var inspection = request(HttpMethod.GET, "/session", idleCookie, null, null, null);
        assertThat(inspection.getBody()).containsEntry("expiresAt", idleExpiry.toString());
        clock.advanceSeconds(8);
        assertThat(request(HttpMethod.GET, "/session", idleCookie, null, null, null).getStatusCode().value())
                .isEqualTo(401);

        // A legacy session without LAST_ACTIVITY attribute: routine inspection does not push idle expiry.
        String legacySession = legacySessions.createSessionCookie(account(syntheticEmail()));
        var firstLegacyRead = request(HttpMethod.GET, "/session", legacySession, null, null, null);
        assertThat(firstLegacyRead.getStatusCode().value()).isEqualTo(200);
        Instant legacyInitialExpiry = Instant.parse((String) firstLegacyRead.getBody().get("expiresAt"));
        clock.advanceSeconds(10);
        var secondLegacyRead = request(HttpMethod.GET, "/session", legacySession, null, null, null);
        assertThat(secondLegacyRead.getStatusCode().value()).isEqualTo(200);
        assertThat(secondLegacyRead.getBody()).containsEntry("expiresAt", legacyInitialExpiry.toString());
        clock.advanceSeconds(31);
        assertThat(request(HttpMethod.GET, "/session", legacySession, null, null, null).getStatusCode().value())
                .isEqualTo(401);

        String activeEmail = syntheticEmail();
        account(activeEmail);
        Instant signedInAt = clock.instant();
        var active = signIn(csrf(null), activeEmail, OLD_PASSWORD, null);
        var activeCookie = cookie(active);
        clock.advanceSeconds(15);
        csrf(activeCookie);
        clock.advanceSeconds(15);
        csrf(activeCookie);
        var clamped = request(HttpMethod.GET, "/session", activeCookie, null, null, null);
        assertThat(clamped.getBody()).containsEntry("expiresAt", signedInAt.plusSeconds(40).toString());
        clock.advanceSeconds(10);
        assertThat(request(HttpMethod.GET, "/session", activeCookie, null, null, null).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void recoveryDeduplicatesAndConcurrentRedemptionChangesPasswordOnce() throws Exception {
        String email = syntheticEmail();
        UUID account = account(email);
        var firstSession = cookie(signIn(csrf(null), email, OLD_PASSWORD, null));
        var secondSession = cookie(signIn(csrf(null), email, OLD_PASSWORD, null));
        var recovering = csrf(null);
        String requestKey = UUID.randomUUID().toString();
        var eligible = request(HttpMethod.POST, "/access-recovery", recovering.cookie(), recovering.csrf(),
                Map.of("email", email), requestKey);
        String issuedDigest = query("SELECT token_digest FROM account_recovery_token WHERE account_id = ?", account);
        var retry = request(HttpMethod.POST, "/access-recovery", recovering.cookie(), recovering.csrf(),
                Map.of("email", email.toUpperCase(java.util.Locale.ROOT)), requestKey);
        var ineligible = request(HttpMethod.POST, "/access-recovery", recovering.cookie(), recovering.csrf(),
                Map.of("email", syntheticEmail()), requestKey);
        assertThat(List.of(eligible, retry, ineligible)).allSatisfy(response -> {
            assertThat(response.getStatusCode().value()).isEqualTo(202);
            assertThat(response.getBody()).containsOnlyKeys("accepted", "correlationId").containsEntry("accepted", true);
        });
        assertThat(query("SELECT token_digest FROM account_recovery_token WHERE account_id = ?", account))
                .isEqualTo(issuedDigest);

        String token = randomToken();
        installToken(account, token, "15 minutes");
        var tooShort = reset(recovering, token, "short", requestKey);
        assertThat(tooShort.getStatusCode().value()).isEqualTo(422);
        assertThat(tooShort.getBody()).containsEntry("code", "PASSWORD_TOO_SHORT");
        var concurrent = csrf(null);
        var replacement = "Fictional-replacement-" + UUID.randomUUID();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> reset(recovering, token, replacement, requestKey));
            var b = executor.submit(() -> reset(concurrent, token, replacement, requestKey));
            assertThat(List.of(a.get().getStatusCode().value(), b.get().getStatusCode().value()))
                    .containsExactlyInAnyOrder(200, 422);
        }
        assertThat(reset(recovering, token, replacement, requestKey).getBody())
                .containsEntry("code", "INVALID_RECOVERY_LINK");
        assertThat(query("SELECT count(*) FROM account_access_audit WHERE account_id = ? AND event_type = 'PASSWORD_RESET'",
                account)).isEqualTo("1");
        assertThat(query("SELECT starts_with(password_hash, '{pbkdf2-sha256-600000}') FROM account WHERE id = ?",
                account)).isEqualTo("t");
        assertThat(query("SELECT count(*) FROM spring_session WHERE principal_name = ?::text", account)).isEqualTo("0");
        assertThat(request(HttpMethod.GET, "/session", firstSession, null, null, null).getStatusCode().value()).isEqualTo(401);
        assertThat(request(HttpMethod.GET, "/session", secondSession, null, null, null).getStatusCode().value()).isEqualTo(401);

        // A request finishing after the physical revocation could still persist a session at revision 0.
        String lateOldSession = legacySessions.createSessionCookie(account);
        assertThat(request(HttpMethod.GET, "/session", lateOldSession, null, null, null).getStatusCode().value()).isEqualTo(401);
        assertThat(signIn(recovering, email, OLD_PASSWORD, null).getStatusCode().value()).isEqualTo(401);
        assertThat(signIn(recovering, email, replacement, null).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void expiredAndSuspendedRecoveryCannotChangePassword() throws Exception {
        String email = syntheticEmail();
        UUID account = account(email);
        String expired = randomToken();
        installToken(account, expired, "-1 second");
        var browser = csrf(null);
        String key = UUID.randomUUID().toString();
        var expiredResponse = reset(browser, expired, OLD_PASSWORD, key);
        assertThat(expiredResponse.getBody()).containsEntry("code", "INVALID_RECOVERY_LINK");
        String suspended = randomToken();
        installToken(account, suspended, "15 minutes");
        updateAccountStateAsOwner(account, "SUSPENDED");
        var suspendedResponse = reset(browser, suspended, OLD_PASSWORD, key);
        assertThat(suspendedResponse.getBody()).containsEntry("code", "INVALID_RECOVERY_LINK")
                .containsEntry("detail", expiredResponse.getBody().get("detail"));
        assertThat(query("SELECT credential_revision FROM account WHERE id = ?", account)).isEqualTo("0");
    }

    @Test
    @Order(Integer.MAX_VALUE)
    void directAddressBudgetIgnoresForwardedHeadersAndMissingCsrfCreatesNoSession() {
        var noSession = request(HttpMethod.POST, "/session", null, "unissued-token",
                Map.of("email", syntheticEmail(), "password", OLD_PASSWORD), null);
        assertThat(noSession.getStatusCode().value()).isEqualTo(403);
        assertThat(noSession.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();

        var anonymous = csrf(null);
        clock.advanceSeconds(301);
        var expiredAnonymous = request(HttpMethod.POST, "/session", anonymous.cookie(), anonymous.csrf(),
                Map.of("email", syntheticEmail(), "password", OLD_PASSWORD), null);
        assertThat(expiredAnonymous.getStatusCode().value()).isEqualTo(403);
        assertThat(expiredAnonymous.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(request(HttpMethod.GET, "/session", anonymous.cookie(), null, null, null).getStatusCode().value())
                .isEqualTo(401);

        RestClient direct = RestClient.create("http://localhost:" + port + "/api/v1");
        var admitted = 0;
        ResponseEntity<Map> limited = null;
        for (int index = 1; index <= 13; index++) {
            var response = direct.get().uri("/csrf")
                    .header("X-Forwarded-For", "192.0.2." + index)
                    .retrieve().onStatus(status -> status.isError(), (req, res) -> {}).toEntity(Map.class);
            if (response.getStatusCode().value() == 429) {
                limited = response;
                break;
            }
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            admitted++;
        }
        assertThat(admitted).isPositive().isLessThanOrEqualTo(12);
        assertThat(limited).isNotNull();
        assertThat(limited.getBody()).containsEntry("code", "RATE_LIMITED").doesNotContainKey("token");
        assertThat(limited.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(Integer.parseInt(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isPositive();
    }

    private UUID account(String email) {
        return createAccountAsOwner(email, "Camille Exemple", ENCODER.encode(OLD_PASSWORD));
    }

    private static String syntheticEmail() {
        return "synthetic-" + UUID.randomUUID() + "@example.test";
    }

    private Browser csrf(String cookie) {
        var response = request(HttpMethod.GET, "/csrf", cookie, null, null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return new Browser(cookie == null ? cookie(response) : cookie, (String) response.getBody().get("token"));
    }

    private ResponseEntity<Map> signIn(Browser browser, String email, String password, String returnTo) {
        var payload = new java.util.HashMap<>(Map.of("email", email, "password", password));
        if (returnTo != null) payload.put("returnTo", returnTo);
        return request(HttpMethod.POST, "/session", browser.cookie(), browser.csrf(), payload, null);
    }

    private ResponseEntity<Map> reset(Browser browser, String token, String password, String key) {
        return request(HttpMethod.POST, "/access-recovery/complete", browser.cookie(), browser.csrf(),
                Map.of("token", token, "newPassword", password), key);
    }

    private ResponseEntity<Map> request(HttpMethod method, String path, String cookie, String csrf,
                                        Map<String, String> payload, String key) {
        var request = RestClient.create("http://localhost:" + port + "/api/v1").method(method).uri(path);
        if (cookie != null) request.header(HttpHeaders.COOKIE, cookie);
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (key != null) request.header("Idempotency-Key", key);
        if (payload != null) request.contentType(MediaType.APPLICATION_JSON).body(payload);
        return request.retrieve().onStatus(status -> status.isError(), (req, res) -> {}).toEntity(Map.class);
    }

    private static String cookie(ResponseEntity<?> response) {
        String header = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        if (header == null) throw new AssertionError("Application did not send a session cookie");
        return header.split(";", 2)[0];
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void installToken(UUID accountId, String token, String lifetime) throws Exception {
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        executeAsOwner("""
                INSERT INTO account_recovery_token (token_digest, account_id, expires_at)
                    VALUES ('%s', '%s', clock_timestamp() + INTERVAL '%s')
                ON CONFLICT (account_id) DO UPDATE
                    SET token_digest = EXCLUDED.token_digest, expires_at = EXCLUDED.expires_at
                """.formatted(digest, accountId, lifetime));
    }

    private static String query(String sql, UUID param) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, param);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private record Browser(String cookie, String csrf) {}
}
