package dev.aymen.etaye.records;

import dev.aymen.etaye.AbstractPostgresTest;
import dev.aymen.etaye.TestConnectionLifecycleRecorder;
import dev.aymen.etaye.TestSessionHelper;
import dev.aymen.etaye.TestTransactionFailureHelper;
import dev.aymen.etaye.records.dto.PatientEdit;
import dev.aymen.etaye.records.dto.PatientResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1"
})
public class HttpConnectionReuseIsolationTest extends AbstractPostgresTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestSessionHelper testSessionHelper;

    @Autowired
    private TestTransactionFailureHelper testFailureHelper;

    @Autowired
    private TestConnectionLifecycleRecorder connectionRecorder;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    private String fetchCsrfToken(String sessionCookie) {
        ResponseEntity<Map> response = client().get()
                .uri("/api/v1/csrf")
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve()
                .toEntity(Map.class);
        return (String) response.getBody().get("token");
    }

    @Test
    @DisplayName("C6: HTTP connection reuse across practices: error in Practice A, real capability refusal, local success in Practice B asserting same pg_backend_pid and empty context before setting")
    void c6_httpConnectionReuse_afterErrorAndCapabilityDenial() {
        connectionRecorder.clear();

        // Practice A and B setup
        UUID practiceA = createPracticeAsOwner("Cabinet A " + UUID.randomUUID());
        UUID practiceB = createPracticeAsOwner("Cabinet B " + UUID.randomUUID());

        // Account 1: Clinical write in Practice A
        UUID clinicalUserA = createAccountAsOwner("clinical.a." + UUID.randomUUID() + "@example.com", "Dr A");
        createMembershipAsOwner(clinicalUserA, practiceA, "ACTIVE", new String[]{"CLINICAL_WRITE", "CLINICAL_READ"});
        String sessionCookieA = testSessionHelper.createSessionCookie(clinicalUserA);
        String csrfA = fetchCsrfToken(sessionCookieA);

        // Account 2: Clinical read ONLY in Practice A (lacks CLINICAL_WRITE)
        UUID readOnlyUserA = createAccountAsOwner("readonly.a." + UUID.randomUUID() + "@example.com", "Dr ReadOnly");
        createMembershipAsOwner(readOnlyUserA, practiceA, "ACTIVE", new String[]{"CLINICAL_READ"});
        String sessionCookieReadOnlyA = testSessionHelper.createSessionCookie(readOnlyUserA);
        String csrfReadOnlyA = fetchCsrfToken(sessionCookieReadOnlyA);

        // Account 3: Clinical write in Practice B
        UUID clinicalUserB = createAccountAsOwner("clinical.b." + UUID.randomUUID() + "@example.com", "Dr B");
        createMembershipAsOwner(clinicalUserB, practiceB, "ACTIVE", new String[]{"CLINICAL_WRITE", "CLINICAL_READ"});
        String sessionCookieB = testSessionHelper.createSessionCookie(clinicalUserB);
        String csrfB = fetchCsrfToken(sessionCookieB);

        connectionRecorder.clear();

        // 1. Error in Practice A: injected commit failure rolls back transaction
        testFailureHelper.enableFailureInBeforeCommit();
        try {
            PatientEdit editA = new PatientEdit("Error", "Patient", LocalDate.of(1980, 1, 1), null, null);
            ResponseEntity<Map> errorResponse = client().post()
                    .uri("/api/v1/practices/" + practiceA + "/patients")
                    .header(HttpHeaders.COOKIE, sessionCookieA)
                    .header("X-CSRF-TOKEN", csrfA)
                    .header("Idempotency-Key", "key-c6-err-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(editA)
                    .retrieve()
                    .onStatus(status -> status.value() == 500, (req, res) -> {})
                    .toEntity(Map.class);

            assertThat(errorResponse.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(errorResponse.getBody().get("code")).isEqualTo("INTERNAL_ERROR");
        } finally {
            testFailureHelper.disableFailureInBeforeCommit();
        }

        // 2. Real capability refusal: read-only clinician attempts createPatient in Practice A
        PatientEdit editDenied = new PatientEdit("Denied", "Patient", LocalDate.of(1985, 2, 2), null, null);
        ResponseEntity<Map> deniedResponse = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieReadOnlyA)
                .header("X-CSRF-TOKEN", csrfReadOnlyA)
                .header("Idempotency-Key", "key-c6-denied-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .body(editDenied)
                .retrieve()
                .onStatus(status -> status.value() == 403, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(deniedResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(deniedResponse.getBody().get("code")).isEqualTo("FORBIDDEN");

        // 3. Local success in Practice B: authorized clinical clinician creates patient in Practice B
        PatientEdit editB = new PatientEdit("Success", "PatientB", LocalDate.of(1992, 6, 15), "b@example.com", null);
        ResponseEntity<PatientResponse> successResponse = client().post()
                .uri("/api/v1/practices/" + practiceB + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieB)
                .header("X-CSRF-TOKEN", csrfB)
                .header("Idempotency-Key", "key-c6-success-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .body(editB)
                .retrieve()
                .toEntity(PatientResponse.class);

        assertThat(successResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(successResponse.getBody()).isNotNull();
        assertThat(successResponse.getBody().practiceId()).isEqualTo(practiceB);

        // 4. Assert recorded connection lifecycle invariants across the HTTP calls:
        // Request 1 and Request 3 entered PracticeTransaction
        List<TestConnectionLifecycleRecorder.Snapshot> snapshots = connectionRecorder.getSnapshots();
        assertThat(snapshots).hasSize(2);

        TestConnectionLifecycleRecorder.Snapshot snapshotA = snapshots.get(0);
        TestConnectionLifecycleRecorder.Snapshot snapshotB = snapshots.get(1);

        assertThat(snapshotA.practiceId()).isEqualTo(practiceA);
        assertThat(snapshotB.practiceId()).isEqualTo(practiceB);

        // Both observed an empty context before setting the practice ID (no leak from previous transaction)
        assertThat(snapshotA.contextBeforeSetting()).isNull();
        assertThat(snapshotB.contextBeforeSetting()).isNull();

        // Exact same PostgreSQL backend PID was reused
        assertThat(snapshotB.backendPid())
                .withFailMessage("Hikari pool size 1 must reuse exact same backend PID across requests")
                .isEqualTo(snapshotA.backendPid());
    }
}
