package dev.aymen.etaye.records;

import dev.aymen.etaye.AbstractPostgresTest;
import dev.aymen.etaye.TestSessionHelper;
import dev.aymen.etaye.records.dto.PatientEdit;
import dev.aymen.etaye.records.dto.PatientResponse;
import dev.aymen.etaye.records.dto.RecordResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PatientCarrierOperationsHttpTest extends AbstractPostgresTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestSessionHelper testSessionHelper;

    @Autowired
    private dev.aymen.etaye.TestTransactionFailureHelper testFailureHelper;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    private UUID practiceA;
    private UUID practiceB;

    private UUID clinicalUserA;
    private String sessionCookieClinicalA;

    private UUID clinicalUserB;
    private String sessionCookieClinicalB;

    private UUID adminOnlyUserA;
    private String sessionCookieAdminOnlyA;

    private UUID readOnlyUserA;
    private String sessionCookieReadOnlyA;

    @BeforeEach
    void setUpFixtures() {
        practiceA = createPracticeAsOwner("Cabinet A " + UUID.randomUUID());
        practiceB = createPracticeAsOwner("Cabinet B " + UUID.randomUUID());

        // Account with CLINICAL_WRITE and CLINICAL_READ in Practice A
        clinicalUserA = createAccountAsOwner(
                "clinical.a." + UUID.randomUUID() + "@example.com",
                "Dr A"
        );
        createMembershipAsOwner(clinicalUserA, practiceA, "ACTIVE", new String[]{"CLINICAL_WRITE", "CLINICAL_READ"});
        sessionCookieClinicalA = testSessionHelper.createSessionCookie(clinicalUserA);

        // Account with CLINICAL_READ in Practice B
        clinicalUserB = createAccountAsOwner(
                "clinical.b." + UUID.randomUUID() + "@example.com",
                "Dr B"
        );
        createMembershipAsOwner(clinicalUserB, practiceB, "ACTIVE", new String[]{"CLINICAL_READ"});
        sessionCookieClinicalB = testSessionHelper.createSessionCookie(clinicalUserB);

        // Administrator-only account in Practice A (no clinical capabilities)
        adminOnlyUserA = createAccountAsOwner(
                "admin.a." + UUID.randomUUID() + "@example.com",
                "Admin A"
        );
        createMembershipAsOwner(adminOnlyUserA, practiceA, "ACTIVE", new String[]{"TEAM_MANAGE", "PRACTICE_SETTINGS", "ADMIN_AUDIT"});
        sessionCookieAdminOnlyA = testSessionHelper.createSessionCookie(adminOnlyUserA);

        // Clinician with CLINICAL_READ only in Practice A
        readOnlyUserA = createAccountAsOwner(
                "readonly.a." + UUID.randomUUID() + "@example.com",
                "Dr ReadOnly"
        );
        createMembershipAsOwner(readOnlyUserA, practiceA, "ACTIVE", new String[]{"CLINICAL_READ"});
        sessionCookieReadOnlyA = testSessionHelper.createSessionCookie(readOnlyUserA);
    }

    private String fetchCsrfToken(String sessionCookie) {
        ResponseEntity<Map> response = client().get()
                .uri("/api/v1/csrf")
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve()
                .toEntity(Map.class);

        return (String) response.getBody().get("token");
    }

    private int countRowsInTableDirectly(String table, String filterColumn, Object filterValue) {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(),
                POSTGRES_CONTAINER.getUsername(),
                POSTGRES_CONTAINER.getPassword());
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM " + table + " WHERE " + filterColumn + " = ?")) {
            ps.setObject(1, filterValue);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            return 0;
        } catch (Exception e) {
            throw new RuntimeException("Error counting rows in " + table, e);
        }
    }

    private boolean waitForRuntimeBackendLock(long timeoutSeconds) {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000);
        while (System.currentTimeMillis() < deadline) {
            try (Connection conn = DriverManager.getConnection(
                    POSTGRES_CONTAINER.getJdbcUrl(),
                    POSTGRES_CONTAINER.getUsername(),
                    POSTGRES_CONTAINER.getPassword());
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT count(*) FROM pg_stat_activity WHERE usename = ? AND wait_event_type = 'Lock'")) {
                ps.setString(1, RUNTIME_USER);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // Ignore transient observation connection errors during polling
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @Test
    @DisplayName("C1: Authorized create gives 201, quoted ETag, and exactly one row in all 5 tables")
    void c1_authorizedCreatePatient_commitsAllFiveTablesAndReturns201() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-" + UUID.randomUUID();

        int initialPatientCount = countRowsInTableDirectly("patient", "practice_id", practiceA);
        int initialRecordCount = countRowsInTableDirectly("patient_record", "practice_id", practiceA);
        int initialAuditCount = countRowsInTableDirectly("audit_event", "practice_id", practiceA);
        int initialOutboxCount = countRowsInTableDirectly("outbox_event", "practice_id", practiceA);
        int initialReceiptCount = countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA);

        PatientEdit edit = new PatientEdit(
                "Jean",
                "Dupont",
                LocalDate.of(1985, 5, 20),
                "jean.dupont@example.com",
                "+33123456789"
        );

        ResponseEntity<PatientResponse> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .toEntity(PatientResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("\"0\"");

        PatientResponse patient = response.getBody();
        assertThat(patient).isNotNull();
        assertThat(patient.id()).isNotNull();
        assertThat(patient.recordId()).isNotNull();
        assertThat(patient.practiceId()).isEqualTo(practiceA);
        assertThat(patient.firstName()).isEqualTo("Jean");
        assertThat(patient.lastName()).isEqualTo("Dupont");
        assertThat(patient.version()).isEqualTo(0L);

        UUID patientId = patient.id();

        // Verify counts filtered by practice_id before and after
        assertThat(countRowsInTableDirectly("patient", "practice_id", practiceA)).isEqualTo(initialPatientCount + 1);
        assertThat(countRowsInTableDirectly("patient_record", "practice_id", practiceA)).isEqualTo(initialRecordCount + 1);
        assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isEqualTo(initialAuditCount + 1);
        assertThat(countRowsInTableDirectly("outbox_event", "practice_id", practiceA)).isEqualTo(initialOutboxCount + 1);
        assertThat(countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA)).isEqualTo(initialReceiptCount + 1);

        // Verify exactly one row in all 5 tables in PostgreSQL for this specific patient
        assertThat(countRowsInTableDirectly("patient", "id", patientId)).isEqualTo(1);
        assertThat(countRowsInTableDirectly("patient_record", "patient_id", patientId)).isEqualTo(1);
        assertThat(countRowsInTableDirectly("audit_event", "resource_id", patientId)).isEqualTo(1);
        assertThat(countRowsInTableDirectly("outbox_event", "resource_id", patientId)).isEqualTo(1);
        assertThat(countRowsInTableDirectly("idempotency_receipt", "response_resource", patientId)).isEqualTo(1);
    }

    @Test
    @DisplayName("C2: Injected failure in beforeCommit after flushing all writes rolls back all 5 tables (0 rows)")
    void c2_injectedFailureAfterDomainWrite_rollsBackAllFiveTables() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-fail-" + UUID.randomUUID();

        PatientEdit edit = new PatientEdit(
                "Rollback",
                "Patient",
                LocalDate.of(1990, 1, 1),
                null,
                null
        );

        int patientCountBefore = countRowsInTableDirectly("patient", "practice_id", practiceA);
        int recordCountBefore = countRowsInTableDirectly("patient_record", "practice_id", practiceA);
        int auditCountBefore = countRowsInTableDirectly("audit_event", "practice_id", practiceA);
        int outboxCountBefore = countRowsInTableDirectly("outbox_event", "practice_id", practiceA);
        int receiptCountBefore = countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA);

        testFailureHelper.enableFailureInBeforeCommit();
        try {
            ResponseEntity<Map> response = client().post()
                    .uri("/api/v1/practices/" + practiceA + "/patients")
                    .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                    .header("X-CSRF-TOKEN", csrfToken)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(edit)
                    .retrieve()
                    .onStatus(status -> status.value() == 500, (req, res) -> {})
                    .toEntity(Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().get("code")).isEqualTo("INTERNAL_ERROR");

            // Verify that the beforeCommit failure hook was actually reached
            assertThat(testFailureHelper.getReachedCount()).isGreaterThan(0);

            // Assert row counts of all five tables filtered by practice_id before and after, from an independent observation connection
            assertThat(countRowsInTableDirectly("patient", "practice_id", practiceA)).isEqualTo(patientCountBefore);
            assertThat(countRowsInTableDirectly("patient_record", "practice_id", practiceA)).isEqualTo(recordCountBefore);
            assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isEqualTo(auditCountBefore);
            assertThat(countRowsInTableDirectly("outbox_event", "practice_id", practiceA)).isEqualTo(outboxCountBefore);
            assertThat(countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA)).isEqualTo(receiptCountBefore);
        } finally {
            testFailureHelper.disableFailureInBeforeCommit();
        }
    }

    @Test
    @DisplayName("C3: Practitioner of practice B reading record of practice A gets indistinguishable 404")
    void c3_crossPracticeRecordRead_returnsIndistinguishable404() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-cross-" + UUID.randomUUID();

        PatientEdit edit = new PatientEdit("Alice", "Martin", LocalDate.of(1982, 3, 14), null, null);
        PatientResponse patient = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .body(PatientResponse.class);

        UUID recordAId = patient.recordId();

        // 1. Practice B practitioner reads Practice A record by UUID -> 404
        ResponseEntity<Map> crossResponse = client().get()
                .uri("/api/v1/practices/" + practiceB + "/records/" + recordAId)
                .header(HttpHeaders.COOKIE, sessionCookieClinicalB)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(crossResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(crossResponse.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(crossResponse.getBody().get("code")).isEqualTo("NOT_FOUND");
        assertThat(crossResponse.getBody().get("correlationId")).isNotNull();

        // 2. Practice B practitioner reads non-existent random UUID -> 404
        UUID nonExistentId = UUID.randomUUID();
        ResponseEntity<Map> nonExistentResponse = client().get()
                .uri("/api/v1/practices/" + practiceB + "/records/" + nonExistentId)
                .header(HttpHeaders.COOKIE, sessionCookieClinicalB)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(nonExistentResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(nonExistentResponse.getBody().get("code")).isEqualTo(crossResponse.getBody().get("code"));

        // 3. Positive control: Practice A clinician reading record A -> 200 OK
        ResponseEntity<RecordResponse> readSuccess = client().get()
                .uri("/api/v1/practices/" + practiceA + "/records/" + recordAId)
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .retrieve()
                .toEntity(RecordResponse.class);

        assertThat(readSuccess.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readSuccess.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("\"0\"");
        assertThat(readSuccess.getBody().id()).isEqualTo(recordAId);
        assertThat(readSuccess.getBody().patient().id()).isEqualTo(patient.id());
    }

    @Test
    @DisplayName("C4: Missing capability (admin-only membership and read-only) gives 403 and NO audit/outbox rows")
    void c4_missingCapability_returns403AndCommitsNoAuditOrOutbox() {
        String csrfAdmin = fetchCsrfToken(sessionCookieAdminOnlyA);
        String idempotencyKey = "key-denied-" + UUID.randomUUID();

        PatientEdit edit = new PatientEdit("Denied", "Candidate", LocalDate.of(1995, 8, 12), null, null);

        int initialAuditCount = countRowsInTableDirectly("audit_event", "practice_id", practiceA);
        int initialOutboxCount = countRowsInTableDirectly("outbox_event", "practice_id", practiceA);

        // 1. Administrator without CLINICAL_WRITE attempts createPatient
        ResponseEntity<Map> adminResponse = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieAdminOnlyA)
                .header("X-CSRF-TOKEN", csrfAdmin)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .onStatus(status -> status.value() == 403, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(adminResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(adminResponse.getBody().get("code")).isEqualTo("FORBIDDEN");
        assertThat(adminResponse.getBody().get("correlationId")).isNotNull();

        // 2. Clinician with CLINICAL_READ only attempts createPatient
        String csrfReadOnly = fetchCsrfToken(sessionCookieReadOnlyA);
        ResponseEntity<Map> readOnlyResponse = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieReadOnlyA)
                .header("X-CSRF-TOKEN", csrfReadOnly)
                .header("Idempotency-Key", idempotencyKey + "-2")
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .onStatus(status -> status.value() == 403, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(readOnlyResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(readOnlyResponse.getBody().get("code")).isEqualTo("FORBIDDEN");
        assertThat(readOnlyResponse.getBody().get("correlationId")).isNotNull();

        // Verify AT-038-B invariant: NO domain audit or outbox row is committed for the failed mutation
        int finalAuditCount = countRowsInTableDirectly("audit_event", "practice_id", practiceA);
        int finalOutboxCount = countRowsInTableDirectly("outbox_event", "practice_id", practiceA);

        assertThat(finalAuditCount).isEqualTo(initialAuditCount);
        assertThat(finalOutboxCount).isEqualTo(initialOutboxCount);
    }

    @Test
    @DisplayName("C7: Idempotency: same key/body returns 201 with identical patient; changed body returns 409")
    void c7_idempotencyReplayAndConflict() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-idemp-" + UUID.randomUUID();

        // Baseline counts of all five tables before creation
        int p0 = countRowsInTableDirectly("patient", "practice_id", practiceA);
        int r0 = countRowsInTableDirectly("patient_record", "practice_id", practiceA);
        int a0 = countRowsInTableDirectly("audit_event", "practice_id", practiceA);
        int o0 = countRowsInTableDirectly("outbox_event", "practice_id", practiceA);
        int i0 = countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA);

        PatientEdit initialEdit = new PatientEdit(
                "Idempotent",
                "User",
                LocalDate.of(1988, 12, 1),
                "idemp@example.com",
                null
        );

        // 1. Initial creation -> 201 Created
        ResponseEntity<PatientResponse> firstResponse = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(initialEdit)
                .retrieve()
                .toEntity(PatientResponse.class);

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(firstResponse.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("\"0\"");
        UUID createdPatientId = firstResponse.getBody().id();

        // Verify all five tables incremented by 1
        assertThat(countRowsInTableDirectly("patient", "practice_id", practiceA)).isEqualTo(p0 + 1);
        assertThat(countRowsInTableDirectly("patient_record", "practice_id", practiceA)).isEqualTo(r0 + 1);
        assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isEqualTo(a0 + 1);
        assertThat(countRowsInTableDirectly("outbox_event", "practice_id", practiceA)).isEqualTo(o0 + 1);
        assertThat(countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA)).isEqualTo(i0 + 1);

        // 2. Replay with exact same key and body -> returns same patient, no new row
        ResponseEntity<PatientResponse> replayResponse = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(initialEdit)
                .retrieve()
                .toEntity(PatientResponse.class);

        assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replayResponse.getHeaders().getFirst(HttpHeaders.ETAG))
                .isEqualTo(firstResponse.getHeaders().getFirst(HttpHeaders.ETAG));
        assertThat(replayResponse.getBody()).isEqualTo(firstResponse.getBody());

        // Exactly 1 patient in database with this ID
        assertThat(countRowsInTableDirectly("patient", "id", createdPatientId)).isEqualTo(1);

        // Verify all five table counts remained unchanged after replay
        assertThat(countRowsInTableDirectly("patient", "practice_id", practiceA)).isEqualTo(p0 + 1);
        assertThat(countRowsInTableDirectly("patient_record", "practice_id", practiceA)).isEqualTo(r0 + 1);
        assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isEqualTo(a0 + 1);
        assertThat(countRowsInTableDirectly("outbox_event", "practice_id", practiceA)).isEqualTo(o0 + 1);
        assertThat(countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA)).isEqualTo(i0 + 1);

        // 3. Replay with same key but DIFFERENT body -> 409 IDEMPOTENCY_CONFLICT
        PatientEdit conflictingEdit = new PatientEdit(
                "Different",
                "User",
                LocalDate.of(1988, 12, 1),
                "conflict@example.com",
                null
        );

        ResponseEntity<Map> conflictResponse = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(conflictingEdit)
                .retrieve()
                .onStatus(status -> status.value() == 409, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(conflictResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflictResponse.getBody().get("code")).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(conflictResponse.getBody().get("correlationId")).isNotNull();

        // Verify all five table counts remained unchanged after conflict
        assertThat(countRowsInTableDirectly("patient", "practice_id", practiceA)).isEqualTo(p0 + 1);
        assertThat(countRowsInTableDirectly("patient_record", "practice_id", practiceA)).isEqualTo(r0 + 1);
        assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isEqualTo(a0 + 1);
        assertThat(countRowsInTableDirectly("outbox_event", "practice_id", practiceA)).isEqualTo(o0 + 1);
        assertThat(countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA)).isEqualTo(i0 + 1);
    }

    @Test
    @DisplayName("C9: Unsafe request without valid CSRF header is rejected with 403 Forbidden")
    void c9_unsafeRequestWithoutCsrf_rejectedWith403() {
        String idempotencyKey = "key-nocsrf-" + UUID.randomUUID();
        PatientEdit edit = new PatientEdit("No", "Csrf", LocalDate.of(1991, 1, 1), null, null);

        ResponseEntity<Map> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA) // Session present, CSRF header absent
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .onStatus(status -> status.value() == 403, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("CSRF_REJECTED");
        assertThat(response.getBody().get("correlationId")).isNotNull();
    }

    @Test
    @DisplayName("C10: Invalid Idempotency-Key (missing or wrong length) returns 400 Bad Request RFC 9457")
    void c10_invalidIdempotencyKey_returns400Problem() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        PatientEdit edit = new PatientEdit("Short", "Key", LocalDate.of(1991, 1, 1), null, null);

        // 1. Idempotency-Key with < 16 characters
        ResponseEntity<Map> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", "too-short")
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .onStatus(status -> status.value() == 400, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody().get("type")).isEqualTo("about:blank");
        assertThat(response.getBody().get("title")).isEqualTo("Bad Request");
        assertThat(response.getBody().get("status")).isEqualTo(400);
        assertThat(response.getBody().get("detail")).isEqualTo("Invalid idempotency key");
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_IDEMPOTENCY_KEY");
        assertThat(response.getBody().get("correlationId")).isNotNull();

        String bodyString = response.getBody().toString();
        assertThat(bodyString).doesNotContain("Exception");
        assertThat(bodyString).doesNotContain("stackTrace");
        assertThat(bodyString).doesNotContain("org.postgresql");
        assertThat(bodyString).doesNotContain("SELECT ");

        // 2. Idempotency-Key missing entirely
        ResponseEntity<Map> missingHeaderResponse = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .onStatus(status -> status.value() == 400, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(missingHeaderResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingHeaderResponse.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(missingHeaderResponse.getBody().get("code")).isEqualTo("INVALID_IDEMPOTENCY_KEY");
    }

    @Test
    @DisplayName("CO-1: Concurrent requests with same key give consistent outcome and zero duplicates with deterministic overlap")
    void concurrentRequestsWithSameKey_giveConsistentOutcomeAndZeroDuplicates() throws Exception {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-concurrent-" + UUID.randomUUID();

        PatientEdit edit = new PatientEdit(
                "Concurrent",
                "Patient",
                LocalDate.of(1988, 7, 10),
                "concurrent@example.com",
                "+33611223344"
        );

        int patientCountBefore = countRowsInTableDirectly("patient", "practice_id", practiceA);
        int recordCountBefore = countRowsInTableDirectly("patient_record", "practice_id", practiceA);
        int auditCountBefore = countRowsInTableDirectly("audit_event", "practice_id", practiceA);
        int outboxCountBefore = countRowsInTableDirectly("outbox_event", "practice_id", practiceA);
        int receiptCountBefore = countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA);

        CountDownLatch reachedLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        testFailureHelper.enablePauseInBeforeCommit(reachedLatch, releaseLatch);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);

        try {
            java.util.concurrent.Callable<ResponseEntity<PatientResponse>> requestTask = () -> {
                return client().post()
                        .uri("/api/v1/practices/" + practiceA + "/patients")
                        .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(edit)
                        .retrieve()
                        .onStatus(status -> status.is5xxServerError() || status.is4xxClientError(), (req, res) -> {})
                        .toEntity(PatientResponse.class);
            };

            // 1. Start request 1 on its own thread
            java.util.concurrent.Future<ResponseEntity<PatientResponse>> future1 = executor.submit(requestTask);

            // 2. Wait for its "reached beforeCommit" latch (bounded 15s)
            boolean reached = reachedLatch.await(15, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(reached).as("Request 1 must reach beforeCommit within 15 seconds").isTrue();

            // 3. Start request 2
            java.util.concurrent.Future<ResponseEntity<PatientResponse>> future2 = executor.submit(requestTask);

            // 4. Poll pg_stat_activity until at least one runtime backend has wait_event_type = 'Lock' (bounded 10s)
            boolean lockObserved = waitForRuntimeBackendLock(10);
            assertThat(lockObserved).as("At least one runtime backend must have wait_event_type = 'Lock' in pg_stat_activity").isTrue();

            // 5. Only then release request 1
            releaseLatch.countDown();

            // 6. Collect both
            ResponseEntity<PatientResponse> res1 = future1.get(15, java.util.concurrent.TimeUnit.SECONDS);
            ResponseEntity<PatientResponse> res2 = future2.get(15, java.util.concurrent.TimeUnit.SECONDS);

            // Assert: never a 500, two 201 with the same patient id and recordId (or one 201 and one 409)
            assertThat(res1.getStatusCode()).isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(res2.getStatusCode()).isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

            assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            if (res2.getStatusCode() == HttpStatus.CREATED) {
                assertThat(res1.getBody()).isNotNull();
                assertThat(res2.getBody()).isNotNull();
                assertThat(res1.getBody().id()).isEqualTo(res2.getBody().id());
                assertThat(res1.getBody().recordId()).isEqualTo(res2.getBody().recordId());
            } else {
                assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            }

            UUID createdPatientId = res1.getBody().id();

            // Exactly one more patient/record/audit/outbox/receipt row for practice A
            assertThat(countRowsInTableDirectly("patient", "practice_id", practiceA)).isEqualTo(patientCountBefore + 1);
            assertThat(countRowsInTableDirectly("patient_record", "practice_id", practiceA)).isEqualTo(recordCountBefore + 1);
            assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isEqualTo(auditCountBefore + 1);
            assertThat(countRowsInTableDirectly("outbox_event", "practice_id", practiceA)).isEqualTo(outboxCountBefore + 1);
            assertThat(countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA)).isEqualTo(receiptCountBefore + 1);

            assertThat(countRowsInTableDirectly("patient", "id", createdPatientId)).isEqualTo(1);
        } finally {
            testFailureHelper.disablePauseInBeforeCommit();
            releaseLatch.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("A2: Two different valid bodies that old join confused now give 409 Conflict")
    void differentValidBodiesConfusedByOldJoin_nowGive409Conflict() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-confused-" + UUID.randomUUID();

        // Body 1: phone is null
        PatientEdit body1 = new PatientEdit(
                "Claire",
                "Confused",
                LocalDate.of(1992, 4, 15),
                null,
                null
        );

        // Body 2: phone is empty string "" (valid under @Size(max=40), but distinct from null)
        // In the old '|' join, null ? "" produced identical strings, confusing the two bodies.
        PatientEdit body2 = new PatientEdit(
                "Claire",
                "Confused",
                LocalDate.of(1992, 4, 15),
                null,
                ""
        );

        ResponseEntity<PatientResponse> res1 = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body1)
                .retrieve()
                .toEntity(PatientResponse.class);

        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> res2 = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body2)
                .retrieve()
                .onStatus(status -> status.value() == 409, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res2.getBody().get("code")).isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    @DisplayName("A3: CSRF on same session: invalid token is refused with 403 Forbidden")
    void csrf_sameSession_invalidToken_refusedWith403() {
        String idempotencyKey = "key-csrf-inv-" + UUID.randomUUID();
        PatientEdit edit = new PatientEdit("Invalid", "Csrf", LocalDate.of(1991, 1, 1), null, null);

        ResponseEntity<Map> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", "completely-invalid-token-value")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .onStatus(status -> status.value() == 403, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("CSRF_REJECTED");
        assertThat(response.getBody().get("correlationId")).isNotNull();
    }

    @Test
    @DisplayName("A3: CSRF on same session: token supplied only as _csrf parameter is refused with 403 Forbidden")
    void csrf_sameSession_parameterOnly_refusedWith403() {
        String validToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-csrf-param-" + UUID.randomUUID();
        PatientEdit edit = new PatientEdit("Param", "Csrf", LocalDate.of(1991, 1, 1), null, null);

        // Token passed ONLY in query parameter _csrf, NOT in X-CSRF-TOKEN header
        ResponseEntity<Map> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients?_csrf=" + validToken)
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .onStatus(status -> status.value() == 403, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("CSRF_REJECTED");
        assertThat(response.getBody().get("correlationId")).isNotNull();
    }

    @Test
    @DisplayName("A3: CSRF: token from another session is refused with 403 Forbidden")
    void csrf_otherSessionToken_refusedWith403() {
        // Create an entirely separate account and session
        UUID otherAccountId = createAccountAsOwner(
                "other.user." + UUID.randomUUID() + "@example.com",
                "Other User"
        );
        createMembershipAsOwner(otherAccountId, practiceA, "ACTIVE", new String[]{"CLINICAL_WRITE"});
        String sessionCookieOther = testSessionHelper.createSessionCookie(otherAccountId);

        // Fetch CSRF token belonging to the other session
        String tokenFromOtherSession = fetchCsrfToken(sessionCookieOther);

        // Attempt to use tokenFromOtherSession with sessionCookieClinicalA
        String idempotencyKey = "key-csrf-cross-" + UUID.randomUUID();
        PatientEdit edit = new PatientEdit("Cross", "Csrf", LocalDate.of(1991, 1, 1), null, null);

        ResponseEntity<Map> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", tokenFromOtherSession)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .onStatus(status -> status.value() == 403, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("CSRF_REJECTED");
        assertThat(response.getBody().get("correlationId")).isNotNull();
    }

    @Test
    @DisplayName("B1: Schema validation failure (missing required field) returns 400 Problem with fieldErrors")
    void b1_schemaValidationFailure_missingRequiredField_returns400WithFieldErrors() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-val-req-" + UUID.randomUUID();

        // JSON missing required firstName
        String jsonPayload = """
                {
                    "lastName": "Dupont",
                    "dateOfBirth": "1985-05-20"
                }
                """;

        ResponseEntity<Map> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonPayload)
                .retrieve()
                .onStatus(status -> status.value() == 400, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().get("fieldErrors")).isNotNull();

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> fieldErrors = (java.util.List<Map<String, Object>>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).anyMatch(fe -> "firstName".equals(fe.get("field")));
    }

    @Test
    @DisplayName("B1: Schema validation failure (field exceeding max length) returns 400 Problem with fieldErrors")
    void b1_schemaValidationFailure_fieldExceedingMaxLength_returns400WithFieldErrors() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-val-len-" + UUID.randomUUID();

        String overLongName = "A".repeat(101);
        PatientEdit edit = new PatientEdit(overLongName, "Dupont", LocalDate.of(1985, 5, 20), null, null);

        ResponseEntity<Map> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .onStatus(status -> status.value() == 400, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().get("fieldErrors")).isNotNull();

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> fieldErrors = (java.util.List<Map<String, Object>>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).anyMatch(fe -> "firstName".equals(fe.get("field")));
    }

    @Test
    @DisplayName("B1: Business validation failure (future dateOfBirth) returns 422 Unprocessable Content with fieldErrors")
    void b1_businessValidation_futureBirthDate_returns422WithFieldErrors() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-val-future-" + UUID.randomUUID();

        PatientEdit edit = new PatientEdit(
                "Future",
                "Baby",
                LocalDate.now().plusDays(1),
                null,
                null
        );

        ResponseEntity<Map> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .onStatus(status -> status.value() == 422, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().get("status")).isEqualTo(422);

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> fieldErrors = (java.util.List<Map<String, Object>>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).anyMatch(fe -> "dateOfBirth".equals(fe.get("field")));
    }

    @Test
    @DisplayName("B1: Malformed JSON payload returns 400 Bad Request with MALFORMED_REQUEST")
    void b1_malformedJsonPayload_returns400Problem() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-malformed-" + UUID.randomUUID();

        String malformedJson = "{ \"firstName\": \"Jean\", \"invalid\": ";

        ResponseEntity<Map> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(malformedJson)
                .retrieve()
                .onStatus(status -> status.value() == 400, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody().get("code")).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    @DisplayName("B1: Unknown property in JSON payload returns 400 Bad Request with MALFORMED_REQUEST")
    void b1_unknownPropertyInPayload_returns400Problem() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-unknown-prop-" + UUID.randomUUID();

        String jsonWithUnknown = """
                {
                    "firstName": "Jean",
                    "lastName": "Dupont",
                    "dateOfBirth": "1985-05-20",
                    "forbiddenExtraProperty": "hacker"
                }
                """;

        ResponseEntity<Map> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonWithUnknown)
                .retrieve()
                .onStatus(status -> status.value() == 400, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody().get("code")).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    @DisplayName("B1: Invalid parameter type in path (non-UUID) returns 400 Bad Request with INVALID_PARAMETER")
    void b1_invalidParameterTypeInPath_returns400Problem() {
        ResponseEntity<Map> response = client().get()
                .uri("/api/v1/practices/not-a-valid-uuid/records/" + UUID.randomUUID())
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .retrieve()
                .onStatus(status -> status.value() == 400, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_PARAMETER");
    }

    @Test
    @DisplayName("B1: Unknown route returns 404 Not Found RFC 9457")
    void b1_unknownRoute_returns404Problem() {
        ResponseEntity<Map> response = client().get()
                .uri("/api/v1/non-existent-subpath-" + UUID.randomUUID())
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody().get("code")).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("B1: Unsupported HTTP method returns 405 Method Not Allowed RFC 9457")
    void b1_unsupportedMethod_returns405Problem() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        ResponseEntity<Map> response = client().delete()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .retrieve()
                .onStatus(status -> status.value() == 405, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody().get("code")).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    @DisplayName("B1: Unsupported media type returns 415 Unsupported Media Type RFC 9457")
    void b1_unsupportedMediaType_returns415Problem() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-mediatype-" + UUID.randomUUID();

        ResponseEntity<Map> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.TEXT_PLAIN)
                .body("plain text body")
                .retrieve()
                .onStatus(status -> status.value() == 415, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody().get("code")).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("B1: Practice invisibility: unknown practice returns indistinguishable 404 without leaking ID")
    void b1_practiceInvisibility_unknownPracticeReturnsIndistinguishable404() {
        UUID unknownPracticeId = UUID.randomUUID();
        UUID nonExistentRecordId = UUID.randomUUID();

        // 1. Clinical user A queries non-existent record in practice A (authorized practice)
        ResponseEntity<Map> notFoundInPracticeA = client().get()
                .uri("/api/v1/practices/" + practiceA + "/records/" + nonExistentRecordId)
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {})
                .toEntity(Map.class);

        // 2. Clinical user A queries record in unknown/unauthorized practice
        ResponseEntity<Map> notFoundInUnknownPractice = client().get()
                .uri("/api/v1/practices/" + unknownPracticeId + "/records/" + nonExistentRecordId)
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(notFoundInPracticeA.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFoundInUnknownPractice.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Bodies are completely identical in title, status, code, detail
        assertThat(notFoundInUnknownPractice.getBody().get("title")).isEqualTo(notFoundInPracticeA.getBody().get("title"));
        assertThat(notFoundInUnknownPractice.getBody().get("status")).isEqualTo(notFoundInPracticeA.getBody().get("status"));
        assertThat(notFoundInUnknownPractice.getBody().get("code")).isEqualTo(notFoundInPracticeA.getBody().get("code"));
        assertThat(notFoundInUnknownPractice.getBody().get("detail")).isEqualTo(notFoundInPracticeA.getBody().get("detail"));
        assertThat(notFoundInUnknownPractice.getBody().get("detail")).isEqualTo("Resource not found");

        // Verify neither body leaks practice or record IDs
        String bodyString = notFoundInUnknownPractice.getBody().toString();
        assertThat(bodyString).doesNotContain(unknownPracticeId.toString());
        assertThat(bodyString).doesNotContain(nonExistentRecordId.toString());
    }

    @Test
    @DisplayName("B2: Valid client X-Correlation-Id is preserved and echoed in response header")
    void b2_validClientCorrelationId_isEchoedInHeader() {
        String clientCorrelation = "client-trace-12345678";

        ResponseEntity<Map> response = client().get()
                .uri("/api/v1/csrf")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-Correlation-Id", clientCorrelation)
                .retrieve()
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo(clientCorrelation);
    }

    @Test
    @DisplayName("B2: Over-long client X-Correlation-Id (>64 chars) still succeeds and generates a server UUID")
    void b2_overLongCorrelationId_stillSucceedsAndGeneratesServerUuid() {
        String overLongCorrelation = "A".repeat(80);

        ResponseEntity<Map> response = client().get()
                .uri("/api/v1/csrf")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-Correlation-Id", overLongCorrelation)
                .retrieve()
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String returnedCorrelation = response.getHeaders().getFirst("X-Correlation-Id");
        assertThat(returnedCorrelation).isNotNull();
        assertThat(returnedCorrelation).isNotEqualTo(overLongCorrelation);
        // Must be a valid UUID
        assertThat(java.util.UUID.fromString(returnedCorrelation)).isNotNull();
    }

    @Test
    @DisplayName("B2: Invalid characters in client X-Correlation-Id still succeeds and generates a server UUID")
    void b2_invalidCharactersInCorrelationId_stillSucceedsAndGeneratesServerUuid() {
        String invalidCorrelation = "invalid_chars_$$$!!";

        ResponseEntity<Map> response = client().get()
                .uri("/api/v1/csrf")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-Correlation-Id", invalidCorrelation)
                .retrieve()
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String returnedCorrelation = response.getHeaders().getFirst("X-Correlation-Id");
        assertThat(returnedCorrelation).isNotNull();
        assertThat(returnedCorrelation).isNotEqualTo(invalidCorrelation);
        assertThat(java.util.UUID.fromString(returnedCorrelation)).isNotNull();
    }

    @Test
    @DisplayName("B6: Replay after membership is suspended is denied with 403, no projection, and zero new writes")
    void b6_replayAfterMembershipSuspended_isDeniedWithNoProjectionAndNoNewWrite() {
        // Create an account with CLINICAL_WRITE in Practice A
        UUID operatorAccount = createAccountAsOwner(
                "suspendable." + UUID.randomUUID() + "@example.com",
                "Dr Suspendable"
        );
        createMembershipAsOwner(operatorAccount, practiceA, "ACTIVE", new String[]{"CLINICAL_WRITE", "CLINICAL_READ"});
        String operatorSessionCookie = testSessionHelper.createSessionCookie(operatorAccount);
        String csrfToken = fetchCsrfToken(operatorSessionCookie);
        String idempotencyKey = "key-suspend-" + UUID.randomUUID();

        PatientEdit edit = new PatientEdit(
                "Before",
                "Suspension",
                LocalDate.of(1987, 6, 15),
                "suspendable@example.com",
                null
        );

        // 1. Initial request succeeds -> 201 Created
        ResponseEntity<PatientResponse> firstResponse = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, operatorSessionCookie)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .toEntity(PatientResponse.class);

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID createdPatientId = firstResponse.getBody().id();

        // Snapshot row counts across all 5 tables
        int patientCount = countRowsInTableDirectly("patient", "practice_id", practiceA);
        int recordCount = countRowsInTableDirectly("patient_record", "practice_id", practiceA);
        int auditCount = countRowsInTableDirectly("audit_event", "practice_id", practiceA);
        int outboxCount = countRowsInTableDirectly("outbox_event", "practice_id", practiceA);
        int receiptCount = countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA);

        // 2. Suspend membership in database directly as owner
        updateMembershipStateAsOwner(operatorAccount, practiceA, "SUSPENDED");

        // 3. Replay with exact same idempotency key and body
        ResponseEntity<Map> replayResponse = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, operatorSessionCookie)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .onStatus(status -> status.value() == 403, (req, res) -> {})
                .toEntity(Map.class);

        // Assert: 403 Forbidden, no projection returned
        assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(replayResponse.getBody().get("code")).isEqualTo("FORBIDDEN");
        assertThat(replayResponse.getBody().get("detail")).isEqualTo("Access denied");

        // Verify zero new writes across all 5 tables
        assertThat(countRowsInTableDirectly("patient", "practice_id", practiceA)).isEqualTo(patientCount);
        assertThat(countRowsInTableDirectly("patient_record", "practice_id", practiceA)).isEqualTo(recordCount);
        assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isEqualTo(auditCount);
        assertThat(countRowsInTableDirectly("outbox_event", "practice_id", practiceA)).isEqualTo(outboxCount);
        assertThat(countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA)).isEqualTo(receiptCount);
    }

    @Test
    @DisplayName("A1: POST with Accept: text/plain gives 406 problem+json and no writes committed")
    void a1_postWithTextPlainAccept_gives406AndNoWrites() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-a1-406-" + UUID.randomUUID();

        int initialPatient = countRowsInTableDirectly("patient", "practice_id", practiceA);
        int initialRecord = countRowsInTableDirectly("patient_record", "practice_id", practiceA);
        int initialAudit = countRowsInTableDirectly("audit_event", "practice_id", practiceA);
        int initialOutbox = countRowsInTableDirectly("outbox_event", "practice_id", practiceA);
        int initialReceipt = countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA);

        PatientEdit edit = new PatientEdit(
                "TextPlain", "Patient", LocalDate.of(1990, 1, 1), null, null
        );

        ResponseEntity<Map> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .header(HttpHeaders.ACCEPT, "text/plain")
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .onStatus(status -> status.value() == 406, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(406);
        assertThat(response.getBody().get("code")).isEqualTo("NOT_ACCEPTABLE");

        // Verify zero writes committed
        assertThat(countRowsInTableDirectly("patient", "practice_id", practiceA)).isEqualTo(initialPatient);
        assertThat(countRowsInTableDirectly("patient_record", "practice_id", practiceA)).isEqualTo(initialRecord);
        assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isEqualTo(initialAudit);
        assertThat(countRowsInTableDirectly("outbox_event", "practice_id", practiceA)).isEqualTo(initialOutbox);
        assertThat(countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA)).isEqualTo(initialReceipt);
    }

    @Test
    @DisplayName("A1: POST with Accept: application/json gives 201")
    void a1_postWithApplicationJsonAccept_gives201() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-a1-json-" + UUID.randomUUID();

        PatientEdit edit = new PatientEdit(
                "AcceptJson", "Patient", LocalDate.of(1991, 2, 2), null, null
        );

        ResponseEntity<PatientResponse> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .toEntity(PatientResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("A1: POST with Accept: */* gives 201")
    void a1_postWithWildcardAccept_gives201() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-a1-wildcard-" + UUID.randomUUID();

        PatientEdit edit = new PatientEdit(
                "AcceptWildcard", "Patient", LocalDate.of(1992, 3, 3), null, null
        );

        ResponseEntity<PatientResponse> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .toEntity(PatientResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
