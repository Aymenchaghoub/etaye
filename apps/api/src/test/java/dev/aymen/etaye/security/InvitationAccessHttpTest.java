package dev.aymen.etaye.security;

import dev.aymen.etaye.AbstractPostgresTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 1.1 behavior checks C1 to C8 of the context packet, on a real
 * PostgreSQL through real HTTP. Every person, address and token is synthetic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvitationAccessHttpTest extends AbstractPostgresTest {

    private static final String PASSWORD = "Fictional-password-" + UUID.randomUUID();
    private static final Pbkdf2PasswordEncoder ENCODER = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @LocalServerPort private int port;
    @Autowired private dev.aymen.etaye.infrastructure.security.AccountRequestBudget budget;

    @AfterEach
    void resetBudget() {
        budget.reset();
    }

    @Test
    void c1_teamAcceptanceGrantsExactlyTheInvitedMembershipOnce() throws Exception {
        UUID practice = createPracticeAsOwner("Cabinet des Tilleuls");
        UUID issuer = createAccountAsOwner(syntheticEmail(), "Nora Lefebvre");
        String recipient = "alex.riviere@example.test";
        String token = randomToken();
        seedInvitation(practice, issuer, "TEAM_MEMBERSHIP", recipient, token, null,
                new String[]{"CLINICAL_READ", "SCHEDULE_MANAGE"}, "PENDING", "72 hours");

        var anonymous = csrf(null);
        var preview = inspect(anonymous, token);
        assertThat(preview.getStatusCode().value()).isEqualTo(200);
        assertThat(preview.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(preview.getBody())
                .containsEntry("purpose", "TEAM_MEMBERSHIP")
                .containsEntry("practiceName", "Cabinet des Tilleuls")
                .containsEntry("recipientHint", "a•••@example.test")
                .containsEntry("eligibility", "USABLE")
                .hasEntrySatisfying("expiresAt", value -> assertThat(value).isInstanceOf(String.class))
                .doesNotContainKeys("id", "practiceId", "recordId", "intendedRecipient", "token");

        var payload = Map.of("token", token, "displayName", "Alex Rivière", "password", PASSWORD);
        for (String invalidKey : List.of("k".repeat(15), "k".repeat(129))) {
            var rejected = accept(anonymous, payload, invalidKey);
            assertThat(rejected.getStatusCode().value()).isEqualTo(400);
            assertThat(rejected.getBody()).containsEntry("code", "INVALID_IDEMPOTENCY_KEY");
        }
        var accepted = accept(anonymous, payload, "invite.accept.01");
        assertThat(accepted.getStatusCode().value()).isEqualTo(200);
        assertThat(accepted.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(accepted.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("Secure", "HttpOnly", "SameSite=Lax", "Path=/");
        assertThat(accepted.getBody())
                .containsEntry("portalLinkIds", List.of())
                .doesNotContainKeys("token", "password", "csrf");
        var acceptedAccount = (Map<?, ?>) accepted.getBody().get("account");
        assertThat(acceptedAccount.get("email")).isEqualTo(recipient);
        assertThat(acceptedAccount.get("displayName")).isEqualTo("Alex Rivière");
        UUID accountId = UUID.fromString((String) acceptedAccount.get("id"));
        UUID membershipId = UUID.fromString(((List<?>) accepted.getBody().get("membershipIds")).get(0).toString());
        assertThat(query("SELECT state FROM account WHERE id = ?", accountId)).isEqualTo("ACTIVE");
        assertThat(query("SELECT state || ':' || array_to_string(capabilities, ',') FROM membership WHERE id = ?",
                membershipId)).isEqualTo("ACTIVE:CLINICAL_READ,SCHEDULE_MANAGE");
        assertThat(queryPractice(practice, "SELECT state FROM invitation WHERE token_digest = '" + digest(token) + "'"))
                .isEqualTo("ACCEPTED");
        assertThat(query("SELECT count(*) FROM portal_link WHERE practice_id = '" + practice + "'")).isEqualTo("0");
        assertThat(queryPractice(practice, "SELECT count(*) FROM audit_event WHERE event_type = 'INVITATION_ACCEPTED' AND practice_id = '"
                + practice + "' AND actor_account_id = '" + accountId + "'")).isEqualTo("1");

        // Single use: a second attempt under another key grants nothing.
        var second = accept(csrf(null), Map.of("token", token,
                "displayName", "Alex Rivière", "password", PASSWORD), "k".repeat(128));
        assertThat(second.getStatusCode().value()).isEqualTo(422);
        assertThat(second.getBody()).containsEntry("code", "INVITATION_ALREADY_USED");
        assertThat(query("SELECT count(*) FROM membership WHERE practice_id = '" + practice + "'")).isEqualTo("1");
    }

    @Test
    void c2_portalAcceptanceCreatesExactlyOneLinkWithoutStaffCapabilities() throws Exception {
        UUID practice = createPracticeAsOwner("Cabinet des Tilleuls");
        UUID issuer = createAccountAsOwner(syntheticEmail(), "Nora Lefebvre");
        UUID record = seedPatientAndRecord(practice, "1990-03-12");
        String recipient = "marie.collet@example.test";
        String token = randomToken();
        seedInvitation(practice, issuer, "PATIENT_PORTAL", recipient, token, record, new String[]{}, "PENDING", "72 hours");

        var browser = csrf(null);
        String key = UUID.randomUUID().toString();
        var wrongDate = accept(browser, Map.of("token", token,
                "recipientVerification", "1991-03-12", "password", PASSWORD), key);
        var garbage = accept(browser, Map.of("token", token,
                "recipientVerification", "not-a-date", "password", PASSWORD), UUID.randomUUID().toString());
        assertThat(wrongDate.getStatusCode().value()).isEqualTo(422);
        assertThat(wrongDate.getBody()).containsEntry("code", "RECIPIENT_MISMATCH");
        assertThat(garbage.getBody()).containsEntry("code", "RECIPIENT_MISMATCH")
                .containsEntry("detail", wrongDate.getBody().get("detail"));
        assertThat(queryPractice(practice, "SELECT count(*) FROM invitation WHERE state = 'PENDING' AND token_digest = '"
                + digest(token) + "'")).isEqualTo("1");

        assertThat(query("SELECT count(*) FROM account WHERE normalized_email = '" + recipient + "'"))
                .isEqualTo("0");
        var accepted = accept(browser, Map.of("token", token,
                "recipientVerification", "1990-03-12", "password", PASSWORD), key);
        assertThat(accepted.getStatusCode().value()).isEqualTo(200);
        assertThat(accepted.getBody()).containsEntry("membershipIds", List.of());
        assertThat(((List<?>) accepted.getBody().get("portalLinkIds"))).hasSize(1);
        assertThat(accepted.getBody().get("activatedPortalLinkId")).isEqualTo(
                ((List<?>) accepted.getBody().get("portalLinkIds")).getFirst());
        UUID accountId = UUID.fromString((String) ((Map<?, ?>) accepted.getBody().get("account")).get("id"));
        assertThat(query("SELECT display_name FROM account WHERE id = ?", accountId)).isEqualTo("Marie Collet");
        assertThat(query("SELECT count(*) FROM membership WHERE account_id = '" + accountId + "'")).isEqualTo("0");
        assertThat(query("SELECT l.state FROM portal_link l WHERE l.practice_id = '" + practice + "' AND l.record_id = '" + record + "'")).isEqualTo("ACTIVE");
        assertThat(query("SELECT count(*) FROM portal_link WHERE practice_id = '" + practice + "'")).isEqualTo("1");

        // Same key and body replays the same outcome without a second link.
        var replay = accept(csrf(null), Map.of("token", token,
                "recipientVerification", "1990-03-12", "password", PASSWORD), key);
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(query("SELECT count(*) FROM portal_link WHERE practice_id = '" + practice + "'")).isEqualTo("1");
        assertThat(query("SELECT count(*) FROM account WHERE normalized_email = '" + recipient + "'"))
                .isEqualTo("1");

        executeAsOwner("UPDATE account SET credential_revision = credential_revision + 1 WHERE id = '" + accountId + "'");
        var stale = accept(csrf(null), Map.of("token", token,
                "recipientVerification", "1990-03-12", "password", PASSWORD), key);
        assertThat(stale.getBody()).containsEntry("code", "SIGN_IN_REQUIRED");
        assertThat(stale.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void c4_portalConflictRequiresVerificationAndPreservesTheExistingGrant() throws Exception {
        UUID practice = createPracticeAsOwner("Cabinet des Tilleuls");
        UUID issuer = createAccountAsOwner(syntheticEmail(), "Nora Lefebvre");
        UUID record = seedPatientAndRecord(practice, "1990-03-12");
        String linkedToken = randomToken();
        seedInvitation(practice, issuer, "PATIENT_PORTAL", syntheticEmail(), linkedToken,
                record, new String[]{}, "PENDING", "72 hours");
        var linked = accept(csrf(null), Map.of("token", linkedToken, "recipientVerification", "1990-03-12",
                "password", PASSWORD), UUID.randomUUID().toString());
        assertThat(linked.getStatusCode().value()).isEqualTo(200);
        String linkId = (String) linked.getBody().get("activatedPortalLinkId");

        String recipient = syntheticEmail();
        String token = randomToken();
        seedInvitation(practice, issuer, "PATIENT_PORTAL", recipient, token,
                record, new String[]{}, "PENDING", "72 hours");
        var browser = csrf(null);
        var wrong = accept(browser, Map.of("token", token, "recipientVerification", "1991-03-12", "password", PASSWORD),
                UUID.randomUUID().toString());
        assertThat(wrong.getStatusCode().value()).isEqualTo(422);
        assertThat(wrong.getBody()).containsEntry("code", "RECIPIENT_MISMATCH");

        var conflict = accept(browser, Map.of("token", token, "recipientVerification", "1990-03-12", "password", PASSWORD),
                UUID.randomUUID().toString());
        assertThat(conflict.getStatusCode().value()).isEqualTo(409);
        assertThat(conflict.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(conflict.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(conflict.getBody()).containsEntry("code", "PORTAL_LINK_EXISTS");
        assertThat(conflict.getBody().toString()).doesNotContain(record.toString(), linkId, recipient);
        assertThat(query("SELECT id::text FROM portal_link WHERE practice_id = '" + practice + "' AND state = 'ACTIVE'"))
                .isEqualTo(linkId);
        assertThat(query("SELECT count(*) FROM account WHERE normalized_email = '" + recipient + "'")).isEqualTo("0");
        assertThat(queryPractice(practice, "SELECT state FROM invitation WHERE token_digest = '" + digest(token) + "'"))
                .isEqualTo("PENDING");
        assertThat(queryPractice(practice, "SELECT count(*) FROM audit_event WHERE event_type = 'INVITATION_ACCEPTED'"))
                .isEqualTo("1");
    }

    @Test
    void c3_terminalStatesRefuseTypedAndUnknownTokensShareOneNotFoundResponse() throws Exception {
        UUID practice = createPracticeAsOwner("Cabinet des Tilleuls");
        UUID issuer = createAccountAsOwner(syntheticEmail(), "Nora Lefebvre");
        String expired = randomToken();
        seedInvitation(practice, issuer, "TEAM_MEMBERSHIP", syntheticEmail(), expired, null,
                new String[]{"CLINICAL_READ"}, "PENDING", "-1 second");
        String revoked = randomToken();
        seedInvitation(practice, issuer, "PATIENT_PORTAL", syntheticEmail(), revoked,
                seedPatientAndRecord(practice, "1980-01-01"), new String[]{}, "REVOKED", "72 hours");

        var browser = csrf(null);
        var expiredPreview = inspect(browser, expired);
        assertThat(expiredPreview.getBody()).containsEntry("eligibility", "EXPIRED");
        var expiredAccept = accept(browser, Map.of("token", expired,
                "displayName", "Alex", "password", PASSWORD), UUID.randomUUID().toString());
        assertThat(expiredAccept.getBody()).containsEntry("code", "INVITATION_EXPIRED");

        var revokedPreview = inspect(browser, revoked);
        assertThat(revokedPreview.getBody()).containsEntry("eligibility", "REVOKED");
        var revokedAccept = accept(browser, Map.of("token", revoked,
                "recipientVerification", "1980-01-01", "password", PASSWORD), UUID.randomUUID().toString());
        assertThat(revokedAccept.getBody()).containsEntry("code", "INVITATION_REVOKED");

        // An unknown well-formed token is the same 404 as any other unknown resource.
        var unknownInspect = inspect(browser, randomToken());
        var unknownAccept = accept(browser, Map.of("token", randomToken(),
                "displayName", "Alex", "password", PASSWORD), UUID.randomUUID().toString());
        var otherUnknown = accept(browser, Map.of("token", randomToken(),
                "recipientVerification", "1980-01-01", "password", PASSWORD), UUID.randomUUID().toString());
        assertThat(List.of(unknownInspect.getStatusCode().value(), unknownAccept.getStatusCode().value(),
                otherUnknown.getStatusCode().value())).containsExactly(404, 404, 404);
        // The two unknown-token 404 bodies differ only by correlation id.
        assertThat(unknownAccept.getBody())
                .containsEntry("code", "NOT_FOUND")
                .containsEntry("detail", otherUnknown.getBody().get("detail"))
                .containsEntry("status", 404);
        assertThat(inspectBodyEqualsAcceptRefusal(unknownInspect)).isTrue();
        assertThat(queryPractice(practice, "SELECT count(*) FROM audit_event")).isEqualTo("0");
    }

    @Test
    void c3_suspendedPracticeCannotConsumeAnInvitationOrCreateAnAccount() throws Exception {
        UUID practice = createPracticeAsOwner("Cabinet suspendu");
        UUID issuer = createAccountAsOwner(syntheticEmail(), "Nora Lefebvre");
        String recipient = syntheticEmail();
        String token = randomToken();
        seedInvitation(practice, issuer, "TEAM_MEMBERSHIP", recipient, token, null,
                new String[]{"CLINICAL_READ"}, "PENDING", "72 hours");
        executeAsOwner("UPDATE practice SET state = 'SUSPENDED' WHERE id = '" + practice + "'");
        var browser = csrf(null);

        assertThat(inspect(browser, token).getBody()).containsEntry("eligibility", "UNAVAILABLE");
        var refused = accept(browser, Map.of("token", token, "displayName", "Alex", "password", PASSWORD),
                UUID.randomUUID().toString());
        assertThat(refused.getStatusCode().value()).isEqualTo(422);
        assertThat(refused.getBody()).containsEntry("code", "INVITATION_UNAVAILABLE");
        assertNoActivation(practice, recipient);
    }

    private enum ActivationLock { PATIENT_RECORD, ISSUER_ACCOUNT }

    @ParameterizedTest
    @EnumSource(ActivationLock.class)
    void c3_invitationExpiringWhileWaitingOnALockCannotGrantAccess(ActivationLock blocker) throws Exception {
        UUID practice = createPracticeAsOwner("Cabinet des Tilleuls");
        UUID issuer = createAccountAsOwner(syntheticEmail(), "Nora Lefebvre");
        String recipient = syntheticEmail();
        String token = randomToken();
        UUID record = seedPatientAndRecord(practice, "1990-03-12");
        var browser = csrf(null);

        seedInvitation(practice, issuer, "PATIENT_PORTAL", recipient, token, record, new String[]{}, "PENDING", "2.5 seconds");
        try (var connection = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
             var statement = connection.createStatement();
             var observer = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(),
                     POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword());
             var watch = observer.createStatement();
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            connection.setAutoCommit(false);
            statement.execute("SELECT set_config('etaye.practice_id', '" + practice + "', true)");
            statement.execute(switch (blocker) {
                case PATIENT_RECORD -> "SELECT id FROM patient_record WHERE id = '" + record + "' FOR UPDATE";
                case ISSUER_ACCOUNT -> "SELECT id FROM account WHERE id = '" + issuer + "' FOR UPDATE";
            });

            var result = executor.submit(() -> accept(browser, Map.of("token", token,
                    "recipientVerification", "1990-03-12", "password", PASSWORD), UUID.randomUUID().toString()));
            try {
                awaitBlockedAcceptances(watch, 1);
                statement.execute("SET LOCAL statement_timeout = '12s'");
                statement.execute("""
                        SELECT pg_sleep(GREATEST(0, EXTRACT(epoch FROM expires_at - clock_timestamp())) + 0.05)
                        FROM invitation WHERE token_digest = '%s'
                        """.formatted(digest(token)));
            } finally {
                connection.rollback();
            }
            var refused = result.get(10, TimeUnit.SECONDS);
            assertThat(refused.getStatusCode().value()).isEqualTo(422);
            assertThat(refused.getBody()).containsEntry("code", "INVITATION_EXPIRED");
        }
        assertNoActivation(practice, recipient);
    }

    private static void awaitBlockedAcceptances(Statement observer, int expected) throws Exception {
        int blocked = 0;
        for (int attempt = 0; attempt < 20; attempt++) {
            try (var sessions = observer.executeQuery("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE usename = 'etaye_identity' AND wait_event_type = 'Lock'
                      AND query LIKE '%public.accept_invitation%'
                    """)) {
                sessions.next();
                blocked = sessions.getInt(1);
            }
            if (blocked == expected) return;
            Thread.sleep(100);
        }
        assertThat(blocked).as("acceptances waiting on a database lock").isEqualTo(expected);
    }

    private static void assertNoActivation(UUID practice, String recipient) throws Exception {
        assertThat(query("SELECT count(*) FROM account WHERE normalized_email = '" + recipient + "'")).isEqualTo("0");
        assertThat(query("SELECT count(*) FROM membership WHERE practice_id = '" + practice + "'")).isEqualTo("0");
        assertThat(query("SELECT count(*) FROM portal_link WHERE practice_id = '" + practice + "'")).isEqualTo("0");
        assertThat(queryPractice(practice, "SELECT state FROM invitation")).isEqualTo("PENDING");
        assertThat(queryPractice(practice, "SELECT count(*) FROM invitation WHERE accepted_at IS NOT NULL "
                + "OR accepted_by IS NOT NULL OR accept_key_digest IS NOT NULL OR accept_body_hash IS NOT NULL"))
                .isEqualTo("0");
        assertThat(queryPractice(practice, "SELECT count(*) FROM audit_event")).isEqualTo("0");
    }

    @Test
    void c5_suspensionAndForeignSessionsNeverActivateThroughAcceptance() throws Exception {
        UUID practice = createPracticeAsOwner("Cabinet des Tilleuls");
        UUID issuer = createAccountAsOwner(syntheticEmail(), "Nora Lefebvre");

        String suspendedRecipient = syntheticEmail();
        UUID suspendedAccount = createAccountAsOwner(suspendedRecipient, "Léa Marchand");
        updateAccountStateAsOwner(suspendedAccount, "SUSPENDED");
        String suspendedToken = randomToken();
        seedInvitation(practice, issuer, "TEAM_MEMBERSHIP", suspendedRecipient, suspendedToken, null,
                new String[]{"CLINICAL_READ"}, "PENDING", "72 hours");
        var browser = csrf(null);
        var suspendedResponse = accept(browser, Map.of("token", suspendedToken,
                "displayName", "Léa", "password", PASSWORD), UUID.randomUUID().toString());
        assertThat(suspendedResponse.getBody()).containsEntry("code", "SIGN_IN_REQUIRED");
        assertThat(query("SELECT state FROM account WHERE id = ?", suspendedAccount)).isEqualTo("SUSPENDED");
        assertThat(query("SELECT count(*) FROM membership WHERE practice_id = '" + practice + "'")).isEqualTo("0");

        // An existing active account that is not signed in must sign in first.
        String recipient = syntheticEmail();
        UUID recipientAccount = createAccountAsOwner(recipient, "Camille Exemple", ENCODER.encode(PASSWORD));
        String unsignedToken = randomToken();
        seedInvitation(practice, issuer, "TEAM_MEMBERSHIP", recipient, unsignedToken, null,
                new String[]{"CLINICAL_READ"}, "PENDING", "72 hours");
        var unsigned = accept(browser, Map.of("token", unsignedToken,
                "displayName", "Camille", "password", PASSWORD), UUID.randomUUID().toString());
        assertThat(unsigned.getBody()).containsEntry("code", "SIGN_IN_REQUIRED");

        // A different signed-in identity never activates the invitation.
        String foreignEmail = syntheticEmail();
        createAccountAsOwner(foreignEmail, "Luc Exemple", ENCODER.encode(PASSWORD));
        var foreign = signInBrowser(csrf(null), foreignEmail, PASSWORD);
        var foreignResponse = accept(foreign, Map.of("token", unsignedToken), UUID.randomUUID().toString());
        assertThat(foreignResponse.getBody()).containsEntry("code", "SIGN_IN_REQUIRED");
        assertThat(queryPractice(practice, "SELECT count(*) FROM invitation WHERE state = 'ACCEPTED'")).isEqualTo("0");

        // The matching signed-in recipient accepts without a new password.
        var signedIn = signInBrowser(csrf(null), recipient, PASSWORD);
        String matchingKey = UUID.randomUUID().toString();
        var matching = accept(signedIn, Map.of("token", unsignedToken), matchingKey);
        assertThat(matching.getStatusCode().value()).isEqualTo(200);
        assertThat(((List<?>) matching.getBody().get("membershipIds"))).hasSize(1);

        // A suspended membership is not silently reactivated by a new invitation.
        assertThat(accept(csrf(null), Map.of("token", unsignedToken), matchingKey).getBody())
                .containsEntry("code", "INVITATION_ALREADY_USED");
        updateMembershipStateAsOwner(recipientAccount, practice, "SUSPENDED");
        String again = randomToken();
        seedInvitation(practice, issuer, "TEAM_MEMBERSHIP", recipient, again, null,
                new String[]{"CLINICAL_READ"}, "PENDING", "72 hours");
        var reactivation = accept(csrf(cookie(matching)), Map.of("token", again), UUID.randomUUID().toString());
        assertThat(reactivation.getBody()).containsEntry("code", "SIGN_IN_REQUIRED");
        assertThat(query("SELECT state FROM membership WHERE account_id = '" + recipientAccount
                + "' AND practice_id = '" + practice + "'")).isEqualTo("SUSPENDED");
    }

    @ParameterizedTest(name = "concurrent requests share the same key: {0}")
    @ValueSource(booleans = {false, true})
    void c6_concurrentAcceptanceCreatesOneGrantAndReplayStaysStable(boolean sameKey) throws Exception {
        UUID practice = createPracticeAsOwner("Cabinet des Tilleuls");
        UUID issuer = createAccountAsOwner(syntheticEmail(), "Nora Lefebvre");
        String recipient = syntheticEmail();
        String token = randomToken();
        seedInvitation(practice, issuer, "TEAM_MEMBERSHIP", recipient, token, null,
                new String[]{"CLINICAL_READ"}, "PENDING", "72 hours");
        var payload = Map.<String, String>of("token", token, "displayName", "Alex Rivière", "password", PASSWORD);

        var first = csrf(null);
        var second = csrf(null);
        String firstKey = UUID.randomUUID().toString();
        String secondKey = sameKey ? firstKey : UUID.randomUUID().toString();
        String key;
        try (var gate = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
             var lock = gate.createStatement();
             var observer = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(),
                     POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword());
             var watch = observer.createStatement();
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            gate.setAutoCommit(false);
            lock.execute("SELECT set_config('etaye.practice_id', '" + practice + "', true)");
            lock.execute("SELECT id FROM invitation WHERE token_digest = '" + digest(token) + "' FOR UPDATE");
            var a = executor.submit(() -> accept(first, payload, firstKey));
            var b = executor.submit(() -> accept(second, payload, secondKey));
            try {
                awaitBlockedAcceptances(watch, 2);
            } finally {
                gate.rollback();
            }
            int firstStatus = a.get(10, TimeUnit.SECONDS).getStatusCode().value();
            int secondStatus = b.get(10, TimeUnit.SECONDS).getStatusCode().value();
            assertThat(List.of(firstStatus, secondStatus)).containsExactlyInAnyOrder(200, sameKey ? 200 : 422);
            key = firstStatus == 200 ? firstKey : secondKey;
        }
        assertThat(query("SELECT count(*) FROM membership WHERE practice_id = '" + practice + "'")).isEqualTo("1");
        assertThat(query("SELECT count(*) FROM account WHERE normalized_email = '" + recipient + "'")).isEqualTo("1");
        assertThat(queryPractice(practice, "SELECT count(*) FROM audit_event WHERE event_type = 'INVITATION_ACCEPTED'"))
                .isEqualTo("1");
        assertThat(accept(csrf(null), payload, key).getStatusCode().value()).isEqualTo(200);

        var invalidRetry = accept(csrf(null),
                Map.of("token", token, "displayName", "Alex Rivière", "password", "short"), key);
        assertThat(invalidRetry.getStatusCode().value()).isEqualTo(422);
        assertThat(invalidRetry.getBody()).containsEntry("code", "PASSWORD_TOO_SHORT");

        // A changed body under the winning key conflicts after request validation.
        var conflict = accept(csrf(null),
                Map.of("token", token, "displayName", "Other Name", "password", PASSWORD), key);
        assertThat(conflict.getStatusCode().value()).isEqualTo(409);
        assertThat(conflict.getBody()).containsEntry("code", "IDEMPOTENCY_CONFLICT");

        // The stored replay memory keeps digests only, never the token or the password.
        var stored = queryPractice(practice, "SELECT accept_key_digest || '|' || accept_body_hash FROM invitation WHERE token_digest = '"
                + digest(token) + "'").split("\\|");
        assertThat(stored[0]).matches("[0-9a-f]{64}").doesNotContain(token);
        assertThat(stored[1]).matches("[0-9a-f]{64}").doesNotContain(PASSWORD);
        assertThat(queryPractice(practice, "SELECT count(*) FROM audit_event WHERE safe_metadata::text LIKE '%"
                + token + "%' OR safe_metadata::text LIKE '%" + PASSWORD + "%'")).isEqualTo("0");
    }

    @Test
    void c7_acceptanceTouchesOnlyTheInvitationPracticeUnderRestrictedRoles() throws Exception {
        UUID practiceA = createPracticeAsOwner("Cabinet des Tilleuls");
        UUID practiceB = createPracticeAsOwner("Cabinet du Canal");
        UUID issuer = createAccountAsOwner(syntheticEmail(), "Nora Lefebvre");
        seedInvitation(practiceA, issuer, "TEAM_MEMBERSHIP", syntheticEmail(), randomToken(), null,
                new String[]{"CLINICAL_READ"}, "PENDING", "72 hours");
        String firstName = "É".repeat(100);
        String lastName = "ç".repeat(100);
        UUID recordB = seedPatientAndRecord(practiceB, "1986-02-25", firstName, lastName);
        String token = randomToken();
        seedInvitation(practiceB, issuer, "PATIENT_PORTAL", syntheticEmail(), token, recordB,
                new String[]{}, "PENDING", "72 hours");

        var accepted = accept(csrf(null), Map.of("token", token,
                "recipientVerification", "1986-02-25", "password", PASSWORD), UUID.randomUUID().toString());
        assertThat(accepted.getStatusCode().value()).isEqualTo(200);
        var account = (Map<?, ?>) accepted.getBody().get("account");
        assertThat(account.get("displayName")).isEqualTo(firstName + " " + lastName.substring(0, 99));
        assertThat(queryPractice(practiceB, "SELECT last_name FROM patient")).isEqualTo(lastName);
        assertThat(queryPractice(practiceA, "SELECT count(*) FROM audit_event"))
                .isEqualTo("0");
        assertThat(query("SELECT l.practice_id::text FROM portal_link l WHERE l.record_id = '" + recordB + "'"))
                .isEqualTo(practiceB.toString());

        // The runtime role has no direct access to the invitation table at all.
        assertThat(runtimeSelectThrows("SELECT * FROM invitation")).isTrue();
        // The identity role sees invitations only through the narrow digest function.
        assertThat(identityCanSelectInvitationDirectly()).isFalse();

        // Reuse one physical connection with a preexisting context. The preview must isolate
        // the supplied digest and restore the prior transaction state.
        try (var connection = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), IDENTITY_USER, IDENTITY_PASSWORD);
             var statement = connection.createStatement();
             var preview = connection.prepareStatement("SELECT practice_name FROM find_invitation_by_digest(?)")) {
            connection.setAutoCommit(false);
            statement.execute("SELECT set_config('etaye.practice_id', '" + practiceA + "', true)");
            preview.setString(1, digest(token));
            try (var result = preview.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("Cabinet du Canal");
                assertThat(result.next()).isFalse();
            }
            preview.setString(1, digest(randomToken()));
            try (var result = preview.executeQuery()) {
                assertThat(result.next()).isFalse();
            }
            try (var result = statement.executeQuery("SELECT current_setting('etaye.practice_id', true), "
                    + "current_setting('etaye.invitation_digest', true)")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo(practiceA.toString());
                assertThat(result.getString(2)).isEmpty();
            }
            connection.commit();
        }
    }

    @Test
    void c8_anonymousEndpointsRequireCsrfAndStayBounded() throws Exception {
        var noCsrf = request(HttpMethod.POST, "/invitations/accept", null, "unissued-token",
                Map.of("token", randomToken(), "displayName", "Alex", "password", PASSWORD),
                UUID.randomUUID().toString());
        assertThat(noCsrf.getStatusCode().value()).isEqualTo(403);
        assertThat(noCsrf.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(request(HttpMethod.POST, "/invitations/inspect", null, null,
                Map.of("token", randomToken()), null).getStatusCode().value()).isEqualTo(403);

        var browser = csrf(null);
        var limited = 0;
        ResponseEntity<Map> last = null;
        for (int attempt = 0; attempt <= 13; attempt++) {
            last = accept(browser, Map.of("token", randomToken(),
                    "displayName", "Alex", "password", PASSWORD), UUID.randomUUID().toString());
            if (last.getStatusCode().value() == 429) {
                break;
            }
            limited++;
        }
        assertThat(limited).isPositive().isLessThanOrEqualTo(12);
        assertThat(last.getStatusCode().value()).isEqualTo(429);
        assertThat(last.getBody()).containsEntry("code", "RATE_LIMITED");
        assertThat(Integer.parseInt(last.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isPositive();

        for (int attempt = 0; attempt < 12; attempt++) {
            assertThat(inspect(browser, randomToken()).getStatusCode().value()).isEqualTo(404);
        }
        var inspectLimited = inspect(browser, randomToken());
        assertThat(inspectLimited.getStatusCode().value()).isEqualTo(429);
        assertThat(Integer.parseInt(inspectLimited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isPositive();
    }

    private boolean inspectBodyEqualsAcceptRefusal(ResponseEntity<Map> inspectResponse) {
        var body = (Map<?, ?>) inspectResponse.getBody();
        return "NOT_FOUND".equals(body.get("code")) && "Resource not found".equals(body.get("detail"));
    }

    // Fixtures and helpers -------------------------------------------------

    private static String syntheticEmail() {
        return "synthetic-" + UUID.randomUUID() + "@example.test";
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static UUID seedPatientAndRecord(UUID practiceId, String birthDate) {
        return seedPatientAndRecord(practiceId, birthDate, "Marie", "Collet");
    }

    private static UUID seedPatientAndRecord(UUID practiceId, String birthDate, String firstName, String lastName) {
        UUID patient = UUID.randomUUID();
        UUID record = UUID.randomUUID();
        executeAsOwner("""
                BEGIN;
                SELECT set_config('etaye.practice_id', '%s', true);
                INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth)
                    VALUES ('%s', '%s', '%s', '%s', '%s');
                INSERT INTO patient_record (id, practice_id, patient_id) VALUES ('%s', '%s', '%s');
                COMMIT;
                """.formatted(practiceId, patient, practiceId, firstName, lastName, birthDate, record, practiceId, patient));
        return record;
    }

    private static void seedInvitation(UUID practiceId, UUID issuer, String purpose, String recipient,
                                       String token, UUID recordId, String[] capabilities, String state,
                                       String lifetime) {
        String caps = "{" + String.join(",", capabilities) + "}";
        executeAsOwner("""
                BEGIN;
                SELECT set_config('etaye.practice_id', '%s', true);
                INSERT INTO invitation (practice_id, purpose, intended_recipient, token_digest, record_id,
                        capabilities, issuer_id, state, expires_at)
                VALUES ('%s', '%s', '%s', '%s', %s, '%s', '%s', '%s', clock_timestamp() + INTERVAL '%s');
                COMMIT;
                """.formatted(practiceId, practiceId, purpose, recipient, digestOrThrow(token),
                recordId == null ? "NULL" : "'" + recordId + "'", caps, issuer, state, lifetime));
    }

    private static String digestOrThrow(String token) {
        try {
            return digest(token);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Browser csrf(String cookie) {
        var response = request(HttpMethod.GET, "/csrf", cookie, null, null, null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return new Browser(cookie == null ? cookie(response) : cookie, (String) response.getBody().get("token"));
    }

    private Browser signInBrowser(Browser browser, String email, String password) {
        var response = request(HttpMethod.POST, "/session", browser.cookie(), browser.csrf(),
                Map.of("email", email, "password", password), null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return new Browser(cookie(response), csrf(cookie(response)).csrf());
    }

    private ResponseEntity<Map> inspect(Browser browser, String token) {
        return request(HttpMethod.POST, "/invitations/inspect", browser.cookie(), browser.csrf(),
                Map.of("token", token), null);
    }

    private ResponseEntity<Map> accept(Browser browser, Map<String, String> payload, String key) {
        return request(HttpMethod.POST, "/invitations/accept", browser.cookie(), browser.csrf(), payload, key);
    }

    private ResponseEntity<Map> request(HttpMethod method, String path, String cookie, String csrf,
                                        Map<String, ?> payload, String key) {
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

    private static String query(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static String queryPractice(UUID practice, String sql) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
             var context = connection.prepareStatement("SELECT set_config('etaye.practice_id', ?, true)");
             var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            context.setString(1, practice.toString());
            context.execute();
            try (var result = statement.executeQuery(sql)) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static boolean runtimeSelectThrows(String sql) {
        try (var connection = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), RUNTIME_USER, RUNTIME_PASSWORD);
             var statement = connection.createStatement()) {
            statement.executeQuery(sql);
            return false;
        } catch (SQLException ex) {
            return "42501".equals(ex.getSQLState());
        }
    }

    private static boolean identityCanSelectInvitationDirectly() {
        try (var connection = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), IDENTITY_USER, IDENTITY_PASSWORD);
             var statement = connection.createStatement()) {
            statement.executeQuery("SELECT * FROM invitation");
            return true;
        } catch (SQLException ex) {
            if ("42501".equals(ex.getSQLState())) return false;
            throw new IllegalStateException(ex);
        }
    }

    private record Browser(String cookie, String csrf) {}
}
