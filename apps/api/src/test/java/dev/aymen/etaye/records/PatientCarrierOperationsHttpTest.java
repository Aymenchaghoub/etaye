package dev.aymen.etaye.records;

import dev.aymen.etaye.AbstractPostgresTest;
import dev.aymen.etaye.TestSessionHelper;
import dev.aymen.etaye.records.web.PatientEdit;
import dev.aymen.etaye.records.web.PatientResponse;
import dev.aymen.etaye.records.web.RecordResponse;
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
                "Dr A",
                "hash"
        );
        createMembershipAsOwner(clinicalUserA, practiceA, "ACTIVE", new String[]{"CLINICAL_WRITE", "CLINICAL_READ"});
        sessionCookieClinicalA = testSessionHelper.createSessionCookie(clinicalUserA);

        // Account with CLINICAL_READ in Practice B
        clinicalUserB = createAccountAsOwner(
                "clinical.b." + UUID.randomUUID() + "@example.com",
                "Dr B",
                "hash"
        );
        createMembershipAsOwner(clinicalUserB, practiceB, "ACTIVE", new String[]{"CLINICAL_READ"});
        sessionCookieClinicalB = testSessionHelper.createSessionCookie(clinicalUserB);

        // Administrator-only account in Practice A (no clinical capabilities)
        adminOnlyUserA = createAccountAsOwner(
                "admin.a." + UUID.randomUUID() + "@example.com",
                "Admin A",
                "hash"
        );
        createMembershipAsOwner(adminOnlyUserA, practiceA, "ACTIVE", new String[]{"TEAM_MANAGE", "PRACTICE_SETTINGS", "ADMIN_AUDIT"});
        sessionCookieAdminOnlyA = testSessionHelper.createSessionCookie(adminOnlyUserA);

        // Clinician with CLINICAL_READ only in Practice A
        readOnlyUserA = createAccountAsOwner(
                "readonly.a." + UUID.randomUUID() + "@example.com",
                "Dr ReadOnly",
                "hash"
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

    @Test
    @DisplayName("C1: Authorized create gives 201, quoted ETag, and exactly one row in all 5 tables")
    void c1_authorizedCreatePatient_commitsAllFiveTablesAndReturns201() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-" + UUID.randomUUID();

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

        // Verify exactly one row in all 5 tables in PostgreSQL
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
        UUID createdPatientId = firstResponse.getBody().id();

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
        assertThat(replayResponse.getBody().id()).isEqualTo(createdPatientId);

        // Exactly 1 patient in database with this ID
        assertThat(countRowsInTableDirectly("patient", "id", createdPatientId)).isEqualTo(1);

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
        assertThat(response.getBody().get("code")).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().get("correlationId")).isNotNull();
    }

    @Test
    @DisplayName("C10: Invalid Idempotency-Key (missing or wrong length) returns 400 Bad Request RFC 9457")
    void c10_invalidIdempotencyKey_returns400Problem() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        PatientEdit edit = new PatientEdit("Short", "Key", LocalDate.of(1991, 1, 1), null, null);

        // Idempotency-Key with < 16 characters
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
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_IDEMPOTENCY_KEY");
        assertThat(response.getBody().get("correlationId")).isNotNull();
    }

    @Test
    @DisplayName("A2: Real concurrent HTTP requests with same key across distinct pooled connections give one patient and no duplicates")
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

        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);

        java.util.concurrent.Callable<ResponseEntity<PatientResponse>> requestTask = () -> {
            barrier.await();
            return client().post()
                    .uri("/api/v1/practices/" + practiceA + "/patients")
                    .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                    .header("X-CSRF-TOKEN", csrfToken)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(edit)
                    .retrieve()
                    .toEntity(PatientResponse.class);
        };

        java.util.concurrent.Future<ResponseEntity<PatientResponse>> future1 = executor.submit(requestTask);
        java.util.concurrent.Future<ResponseEntity<PatientResponse>> future2 = executor.submit(requestTask);

        ResponseEntity<PatientResponse> res1 = future1.get();
        ResponseEntity<PatientResponse> res2 = future2.get();
        executor.shutdown();

        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res1.getBody().id()).isEqualTo(res2.getBody().id());
        assertThat(res1.getBody().recordId()).isEqualTo(res2.getBody().recordId());

        UUID createdPatientId = res1.getBody().id();

        assertThat(countRowsInTableDirectly("patient", "practice_id", practiceA)).isEqualTo(patientCountBefore + 1);
        assertThat(countRowsInTableDirectly("patient_record", "practice_id", practiceA)).isEqualTo(recordCountBefore + 1);
        assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isEqualTo(auditCountBefore + 1);
        assertThat(countRowsInTableDirectly("outbox_event", "practice_id", practiceA)).isEqualTo(outboxCountBefore + 1);
        assertThat(countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA)).isEqualTo(receiptCountBefore + 1);

        assertThat(countRowsInTableDirectly("patient", "id", createdPatientId)).isEqualTo(1);
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
        assertThat(response.getBody().get("code")).isEqualTo("FORBIDDEN");
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
        assertThat(response.getBody().get("code")).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().get("correlationId")).isNotNull();
    }

    @Test
    @DisplayName("A3: CSRF: token from another session is refused with 403 Forbidden")
    void csrf_otherSessionToken_refusedWith403() {
        // Create an entirely separate account and session
        UUID otherAccountId = createAccountAsOwner(
                "other.user." + UUID.randomUUID() + "@example.com",
                "Other User",
                "hash"
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
        assertThat(response.getBody().get("code")).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().get("correlationId")).isNotNull();
    }
}
