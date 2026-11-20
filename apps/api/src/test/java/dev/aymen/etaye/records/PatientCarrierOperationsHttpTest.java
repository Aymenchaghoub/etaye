package dev.aymen.etaye.records;

import dev.aymen.etaye.AbstractPostgresTest;
import dev.aymen.etaye.MutableTestClock;
import dev.aymen.etaye.TestSessionHelper;
import dev.aymen.etaye.audit.service.AuditService;
import dev.aymen.etaye.records.dto.PatientEdit;
import dev.aymen.etaye.records.dto.PatientResponse;
import dev.aymen.etaye.records.dto.RecordResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PatientCarrierOperationsHttpTest.DenialAuditTestConfig.class)
public class PatientCarrierOperationsHttpTest extends AbstractPostgresTest {

    private static final String CREATE_OPERATION = "POST /api/v1/practices/{practiceId}/patients";
    private static final String READ_OPERATION = "GET /api/v1/practices/{practiceId}/records/{recordId}";

    @LocalServerPort
    private int port;

    @Autowired
    private TestSessionHelper testSessionHelper;

    @Autowired
    private dev.aymen.etaye.TestTransactionFailureHelper testFailureHelper;

    @Autowired
    private dev.aymen.etaye.infrastructure.idempotency.IdempotencyService idempotencyService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MutableTestClock denialClock;

    @Autowired
    private FailingDenialAuditService auditService;

    @TestConfiguration(proxyBeanMethods = false)
    static class DenialAuditTestConfig {

        @Bean
        MutableTestClock denialClock() {
            return new MutableTestClock(Instant.parse("2026-11-20T12:00:00Z"));
        }

        @Bean
        @Primary
        FailingDenialAuditService denialTestAuditService(JdbcTemplate jdbcTemplate) {
            return new FailingDenialAuditService(jdbcTemplate);
        }
    }

    static class FailingDenialAuditService extends AuditService {
        private static final String FAILURE_DETAIL = "synthetic audit exception detail";
        private volatile UUID failingActor;

        FailingDenialAuditService(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate);
        }

        @Override
        public void recordEvent(UUID practiceId, UUID actorAccountId, String eventType, String resourceType,
                                UUID resourceId, Long resourceVersion, String outcome, String correlationId,
                                String safeMetadataJson) {
            super.recordEvent(practiceId, actorAccountId, eventType, resourceType, resourceId, resourceVersion,
                    outcome, correlationId, safeMetadataJson);
            if ("ACCESS_DENIED".equals(eventType) && actorAccountId.equals(failingActor)) {
                throw new IllegalStateException(FAILURE_DETAIL);
            }
        }
    }

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

        // Account with CLINICAL_WRITE and CLINICAL_READ in Practice B
        clinicalUserB = createAccountAsOwner(
                "clinical.b." + UUID.randomUUID() + "@example.com",
                "Dr B"
        );
        createMembershipAsOwner(clinicalUserB, practiceB, "ACTIVE", new String[]{"CLINICAL_WRITE", "CLINICAL_READ"});
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

    private void assertDenialEvent(UUID auditPracticeId, UUID actor, String correlationId,
                                   Map<String, String> metadata) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(), POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword());
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT practice_id, actor_account_id, event_type, resource_type,
                            resource_id, resource_version, outcome, safe_metadata
                     FROM audit_event WHERE correlation_id = ?
                     """)) {
            ps.setString(1, correlationId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject("practice_id")).isEqualTo(auditPracticeId);
                assertThat(rs.getObject("actor_account_id")).isEqualTo(actor);
                assertThat(rs.getString("event_type")).isEqualTo("ACCESS_DENIED");
                assertThat(rs.getString("resource_type")).isEqualTo("API_OPERATION");
                assertThat(rs.getObject("resource_id")).isNull();
                assertThat(rs.getObject("resource_version")).isNull();
                assertThat(rs.getString("outcome")).isEqualTo("DENIED");
                assertThat(objectMapper.readTree(rs.getString("safe_metadata")))
                        .isEqualTo(objectMapper.valueToTree(metadata));
                assertThat(rs.next()).isFalse();
            }
        }
    }

    private ResponseEntity<Map> attemptDeniedCreate(UUID targetPracticeId, String sessionCookie) {
        return client().post()
                .uri("/api/v1/practices/" + targetPracticeId + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookie)
                .header("X-CSRF-TOKEN", fetchCsrfToken(sessionCookie))
                .header("Idempotency-Key", "key-denied-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PatientEdit("Denied", "Candidate", LocalDate.of(1995, 8, 12), null, null))
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
                .toEntity(Map.class);
    }

    private ResponseEntity<Map> attemptDeniedRead(UUID targetPracticeId, String sessionCookie) {
        return client().get()
                .uri("/api/v1/practices/" + targetPracticeId + "/records/" + UUID.randomUUID())
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
                .toEntity(Map.class);
    }

    private String assertDenialProblem(ResponseEntity<Map> response, HttpStatus status, String code, String detail) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        String correlationId = response.getHeaders().getFirst("X-Correlation-Id");
        assertThat(correlationId).isNotBlank();
        assertThat(response.getBody()).isEqualTo(Map.of(
                "type", "about:blank",
                "title", status.getReasonPhrase(),
                "status", status.value(),
                "code", code,
                "detail", detail,
                "correlationId", correlationId));
        return correlationId;
    }

    private void waitForAdvisoryLockContention(long expectedLockKey, long timeoutSeconds) {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000);
        while (System.currentTimeMillis() < deadline) {
            try (Connection conn = DriverManager.getConnection(
                    POSTGRES_CONTAINER.getJdbcUrl(),
                    POSTGRES_CONTAINER.getUsername(),
                    POSTGRES_CONTAINER.getPassword());
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT pid, granted FROM pg_locks " +
                                 "WHERE locktype = 'advisory' AND objsubid = 1 " +
                                 "AND ((classid::bigint << 32) | (objid::bigint & 4294967295)) = ?")) {
                ps.setLong(1, expectedLockKey);
                try (ResultSet rs = ps.executeQuery()) {
                    Long waitingPid = null;
                    Long holderPid = null;
                    while (rs.next()) {
                        long pid = rs.getLong("pid");
                        boolean granted = rs.getBoolean("granted");
                        if (granted) {
                            holderPid = pid;
                        } else {
                            waitingPid = pid;
                        }
                    }
                    if (waitingPid != null && holderPid != null && !waitingPid.equals(holderPid)) {
                        return;
                    }
                }
            } catch (Exception e) {
                // Ignore transient observation connection errors during polling
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while polling for advisory lock contention", e);
            }
        }
        throw new IllegalStateException("Timeout after " + timeoutSeconds + "s: pending advisory lock for key " +
                expectedLockKey + " (locktype='advisory', objsubid=1, granted=false with distinct holder) was never observed in pg_locks");
    }

    private void assertAdvisoryLockHeld(long expectedLockKey) {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(),
                POSTGRES_CONTAINER.getUsername(),
                POSTGRES_CONTAINER.getPassword());
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM pg_locks " +
                             "WHERE locktype = 'advisory' AND objsubid = 1 AND granted = true " +
                             "AND ((classid::bigint << 32) | (objid::bigint & 4294967295)) = ?")) {
            ps.setLong(1, expectedLockKey);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next() && rs.getInt(1) > 0)
                        .as("Advisory lock for key %d must be held", expectedLockKey)
                        .isTrue();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to check held advisory lock", e);
        }
    }

    @Test
    void conditionalRecordReadReturnsEmpty304WithEtag() {
        PatientResponse patient = client().post()
                .uri("/api/v1/practices/{practiceId}/patients", practiceA)
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", fetchCsrfToken(sessionCookieClinicalA))
                .header("Idempotency-Key", "conditional-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PatientEdit("Synthetic", "Conditional", LocalDate.of(1985, 5, 20), null, null))
                .retrieve().body(PatientResponse.class);
        assertThat(patient).isNotNull();
        String path = "/api/v1/practices/{practiceId}/records/{recordId}";
        var initial = client().get().uri(path, practiceA, patient.recordId())
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA).retrieve().toEntity(RecordResponse.class);
        assertThat(initial.getStatusCode()).isEqualTo(HttpStatus.OK);
        String etag = initial.getHeaders().getETag();
        assertThat(etag).isNotBlank();
        var conditional = client().get().uri(path, practiceA, patient.recordId())
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA).header(HttpHeaders.IF_NONE_MATCH, etag)
                .retrieve().toEntity(String.class);
        assertThat(conditional.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(conditional.getHeaders().getETag()).isEqualTo(etag);
        assertThat(conditional.getBody()).isNull();
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

            // B1: Prove all 5 writes were really sent in the transaction before the injected failure
            assertThat(testFailureHelper.getInsertCount("patient")).as("patient insert count before failure").isGreaterThanOrEqualTo(1);
            assertThat(testFailureHelper.getInsertCount("patient_record")).as("patient_record insert count before failure").isGreaterThanOrEqualTo(1);
            assertThat(testFailureHelper.getInsertCount("audit_event")).as("audit_event insert count before failure").isGreaterThanOrEqualTo(1);
            assertThat(testFailureHelper.getInsertCount("outbox_event")).as("outbox_event insert count before failure").isGreaterThanOrEqualTo(1);
            assertThat(testFailureHelper.getInsertCount("idempotency_receipt")).as("idempotency_receipt insert count before failure").isGreaterThanOrEqualTo(1);

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
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    @DisplayName("A6: Forced 500 logs only exception class, code, and correlationId without raw data, SQL, or request UUIDs")
    void forced500_logsNoRawDataOrSqlOrRequestUuids(org.springframework.boot.test.system.CapturedOutput output) {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-logcheck-" + UUID.randomUUID();
        String syntheticName = "SYNTHLOGCHECK";

        PatientEdit edit = new PatientEdit(
                syntheticName,
                "Patient",
                LocalDate.of(1990, 1, 1),
                null,
                null
        );

        testFailureHelper.enableFailureInBeforeCommit(
                new RuntimeException("Simulated SQL error on insert into patient with " + syntheticName + " for practice " + practiceA)
        );

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

            String logs = output.getAll();
            assertThat(logs).contains("Unhandled server exception: class=java.lang.RuntimeException");
            assertThat(logs).doesNotContain(syntheticName);
            assertThat(logs).doesNotContain("insert into");
            assertThat(logs).doesNotContain("select ");
            assertThat(logs).doesNotContain(practiceA.toString());
        } finally {
            testFailureHelper.disableFailureInBeforeCommit();
        }
    }

    @Test
    @DisplayName("C3: Indistinguishable 404 comparison across all 5 access permutations modulo correlationId, plus positive local reads")
    void c3_crossPracticeRecordRead_returnsIndistinguishable404() {
        // Setup Practice A patient and record
        String csrfTokenA = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKeyA = "key-cross-a-" + UUID.randomUUID();
        PatientEdit editA = new PatientEdit("Alice", "Martin", LocalDate.of(1982, 3, 14), null, null);
        PatientResponse patientA = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfTokenA)
                .header("Idempotency-Key", idempotencyKeyA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(editA)
                .retrieve()
                .body(PatientResponse.class);
        UUID recordAId = patientA.recordId();

        // Setup Practice B patient and record
        String csrfTokenB = fetchCsrfToken(sessionCookieClinicalB);
        String idempotencyKeyB = "key-cross-b-" + UUID.randomUUID();
        PatientEdit editB = new PatientEdit("Bob", "Dupont", LocalDate.of(1975, 7, 22), null, null);
        PatientResponse patientB = client().post()
                .uri("/api/v1/practices/" + practiceB + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalB)
                .header("X-CSRF-TOKEN", csrfTokenB)
                .header("Idempotency-Key", idempotencyKeyB)
                .contentType(MediaType.APPLICATION_JSON)
                .body(editB)
                .retrieve()
                .body(PatientResponse.class);
        UUID recordBId = patientB.recordId();

        UUID nonExistentRecordId = UUID.randomUUID();
        UUID unknownPracticeId = UUID.randomUUID();

        // 1. Record that does not exist in an authorized practice (Practice A clinician)
        ResponseEntity<Map> resNonExistent = client().get()
                .uri("/api/v1/practices/" + practiceA + "/records/" + nonExistentRecordId)
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {})
                .toEntity(Map.class);

        // 2. Record of practice B requested through practice A route by a member of practice A
        ResponseEntity<Map> resRecordBViaPracticeA = client().get()
                .uri("/api/v1/practices/" + practiceA + "/records/" + recordBId)
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {})
                .toEntity(Map.class);

        // 3. The same record id requested through an existing practice where the caller has no membership (member of A calls practice B route)
        ResponseEntity<Map> resRecordAViaPracticeB = client().get()
                .uri("/api/v1/practices/" + practiceB + "/records/" + recordBId)
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {})
                .toEntity(Map.class);

        // 4. An unknown practice id
        ResponseEntity<Map> resUnknownPractice = client().get()
                .uri("/api/v1/practices/" + unknownPracticeId + "/records/" + recordAId)
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {})
                .toEntity(Map.class);

        // 5. Reverse direction: member of practice B requesting Practice A's record on practice B route
        ResponseEntity<Map> resReverseDirection = client().get()
                .uri("/api/v1/practices/" + practiceB + "/records/" + recordAId)
                .header(HttpHeaders.COOKIE, sessionCookieClinicalB)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {})
                .toEntity(Map.class);

        List<ResponseEntity<Map>> all404Responses = List.of(
                resNonExistent,
                resRecordBViaPracticeA,
                resRecordAViaPracticeB,
                resUnknownPractice,
                resReverseDirection
        );

        Map<String, Object> baselineBody = null;
        for (int i = 0; i < all404Responses.size(); i++) {
            ResponseEntity<Map> resp = all404Responses.get(i);
            assertThat(resp.getStatusCode()).as("Case %d must be 404 Not Found", i + 1).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(resp.getHeaders().getContentType().toString())
                    .as("Case %d Content-Type must be application/problem+json", i + 1)
                    .contains("application/problem+json");
            assertThat(resp.getHeaders().getFirst(HttpHeaders.ETAG))
                    .as("Case %d ETag header must be absent on 404", i + 1)
                    .isNull();

            Map<String, Object> body = resp.getBody();
            assertThat(body).as("Case %d body must not be null", i + 1).isNotNull();
            assertThat(body.get("correlationId")).as("Case %d correlationId must be present", i + 1).isNotNull();

            // Extract entire problem body modulo correlationId
            Map<String, Object> bodyWithoutCorrelationId = new HashMap<>(body);
            bodyWithoutCorrelationId.remove("correlationId");

            if (baselineBody == null) {
                baselineBody = bodyWithoutCorrelationId;
                assertThat(baselineBody.get("status")).isEqualTo(404);
                assertThat(baselineBody.get("code")).isEqualTo("NOT_FOUND");
                assertThat(baselineBody.get("title")).isEqualTo("Not Found");
                assertThat(baselineBody.get("detail")).isEqualTo("Resource not found");
                assertThat(baselineBody.get("type")).isEqualTo("about:blank");
            } else {
                assertThat(bodyWithoutCorrelationId)
                        .as("Entire problem body of case %d must be identical to baseline modulo correlationId", i + 1)
                        .isEqualTo(baselineBody);
            }
        }

        // Positive local reads: Practice A clinician reading Record A -> 200 OK
        ResponseEntity<RecordResponse> readSuccessA = client().get()
                .uri("/api/v1/practices/" + practiceA + "/records/" + recordAId)
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .retrieve()
                .toEntity(RecordResponse.class);

        assertThat(readSuccessA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readSuccessA.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("\"0\"");
        assertThat(readSuccessA.getBody()).isNotNull();
        assertThat(readSuccessA.getBody().id()).isEqualTo(recordAId);
        assertThat(readSuccessA.getBody().patient().id()).isEqualTo(patientA.id());

        // Positive local reads: Practice B clinician reading Record B -> 200 OK
        ResponseEntity<RecordResponse> readSuccessB = client().get()
                .uri("/api/v1/practices/" + practiceB + "/records/" + recordBId)
                .header(HttpHeaders.COOKIE, sessionCookieClinicalB)
                .retrieve()
                .toEntity(RecordResponse.class);

        assertThat(readSuccessB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readSuccessB.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("\"0\"");
        assertThat(readSuccessB.getBody()).isNotNull();
        assertThat(readSuccessB.getBody().id()).isEqualTo(recordBId);
        assertThat(readSuccessB.getBody().patient().id()).isEqualTo(patientB.id());
    }

    @Test
    @DisplayName("C4: Each denied mutation gives 403 and commits only its security audit event")
    void c4_missingCapability_returns403AndCommitsOnlyDenialAudit() throws Exception {
        Map<String, String> metadata = Map.of("reason", "CAPABILITY_MISSING", "operation", CREATE_OPERATION,
                "capability", "CLINICAL_WRITE");

        String adminCorrelationId = assertDenialProblem(attemptDeniedCreate(practiceA, sessionCookieAdminOnlyA),
                HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
        assertDenialEvent(practiceA, adminOnlyUserA, adminCorrelationId, metadata);
        assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isEqualTo(1);

        String readOnlyCorrelationId = assertDenialProblem(attemptDeniedCreate(practiceA, sessionCookieReadOnlyA),
                HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
        assertDenialEvent(practiceA, readOnlyUserA, readOnlyCorrelationId, metadata);
        assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isEqualTo(2);

        assertThat(countRowsInTableDirectly("patient", "practice_id", practiceA)).isZero();
        assertThat(countRowsInTableDirectly("patient_record", "practice_id", practiceA)).isZero();
        assertThat(countRowsInTableDirectly("outbox_event", "practice_id", practiceA)).isZero();
        assertThat(countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA)).isZero();
    }

    @Test
    void foreignPracticeDenialIsAuditedOnlyInTheSoleActivePractice() throws Exception {
        UUID practiceC = createPracticeAsOwner("Cabinet C " + UUID.randomUUID());
        createMembershipAsOwner(clinicalUserA, practiceC, "SUSPENDED", new String[]{"CLINICAL_READ"});

        assertDenialProblem(attemptDeniedRead(practiceA, sessionCookieClinicalA),
                HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
        assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isZero();

        String correlationId = assertDenialProblem(attemptDeniedRead(practiceB, sessionCookieClinicalA),
                HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");

        assertDenialEvent(practiceA, clinicalUserA, correlationId,
                Map.of("reason", "NOT_VISIBLE", "operation", READ_OPERATION));
        assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isEqualTo(1);
        assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceB)).isZero();
        assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceC)).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    @ExtendWith(OutputCaptureExtension.class)
    void foreignPracticeDenialWithoutASoleActiveMembershipIsOnlyLogged(int activeMemberships, CapturedOutput output) {
        if (activeMemberships == 0) {
            updateMembershipStateAsOwner(clinicalUserA, practiceA, "SUSPENDED");
        } else {
            UUID practiceC = createPracticeAsOwner("Cabinet C " + UUID.randomUUID());
            createMembershipAsOwner(clinicalUserA, practiceC, "ACTIVE", new String[]{"CLINICAL_READ"});
        }

        String correlationId = assertDenialProblem(attemptDeniedRead(practiceB, sessionCookieClinicalA),
                HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");

        assertThat(countRowsInTableDirectly("audit_event", "actor_account_id", clinicalUserA)).isZero();
        assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceB)).isZero();
        assertThat(output.getAll()).containsPattern("WARN.*Request rejected:.*code=NOT_FOUND, correlationId=" + correlationId)
                .doesNotContain(practiceB.toString());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void auditFailureRollsBackAndPreservesBothDenialResponses(CapturedOutput output) {
        auditService.failingActor = adminOnlyUserA;
        try {
            String forbiddenId = assertDenialProblem(attemptDeniedCreate(practiceA, sessionCookieAdminOnlyA),
                    HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
            String notFoundId = assertDenialProblem(attemptDeniedRead(practiceB, sessionCookieAdminOnlyA),
                    HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");

            assertThat(countRowsInTableDirectly("audit_event", "actor_account_id", adminOnlyUserA)).isZero();
            String errorMessage = "ERROR.*Authorization denial audit failed: class=java.lang.IllegalStateException, correlationId=";
            assertThat(output.getAll()).containsPattern(errorMessage + forbiddenId).containsPattern(errorMessage + notFoundId)
                    .doesNotContain(FailingDenialAuditService.FAILURE_DETAIL);
        } finally {
            auditService.failingActor = null;
        }
    }

    @Test
    void denialAuditVolumeIsBoundedPerActorOperationAndReason() throws Exception {
        assertDenialProblem(attemptDeniedRead(practiceA, sessionCookieAdminOnlyA),
                HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
        assertThat(countRowsInTableDirectly("audit_event", "actor_account_id", adminOnlyUserA)).isEqualTo(1);

        denialClock.advanceSeconds(59);
        String duplicateId = assertDenialProblem(attemptDeniedRead(practiceA, sessionCookieAdminOnlyA),
                HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
        assertThat(countRowsInTableDirectly("audit_event", "correlation_id", duplicateId)).isZero();

        String differentReasonId = assertDenialProblem(attemptDeniedRead(practiceB, sessionCookieAdminOnlyA),
                HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
        assertDenialEvent(practiceA, adminOnlyUserA, differentReasonId,
                Map.of("reason", "NOT_VISIBLE", "operation", READ_OPERATION));

        String differentOperationId = assertDenialProblem(attemptDeniedCreate(practiceA, sessionCookieAdminOnlyA),
                HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
        assertDenialEvent(practiceA, adminOnlyUserA, differentOperationId,
                Map.of("reason", "CAPABILITY_MISSING", "operation", CREATE_OPERATION, "capability", "CLINICAL_WRITE"));
        assertThat(countRowsInTableDirectly("audit_event", "actor_account_id", adminOnlyUserA)).isEqualTo(3);

        denialClock.advanceSeconds(1);
        String expiredWindowId = assertDenialProblem(attemptDeniedRead(practiceA, sessionCookieAdminOnlyA),
                HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
        assertDenialEvent(practiceA, adminOnlyUserA, expiredWindowId,
                Map.of("reason", "CAPABILITY_MISSING", "operation", READ_OPERATION, "capability", "CLINICAL_READ"));
        assertThat(countRowsInTableDirectly("audit_event", "actor_account_id", adminOnlyUserA)).isEqualTo(4);
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
        assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isZero();
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

            // 4. B2(c): Poll pg_locks from the observation connection until locktype = 'advisory' and granted = false exists
            String keyDigest = idempotencyService.computeSha256(idempotencyKey);
            long expectedLockKey = idempotencyService.computeLockKey(practiceA, clinicalUserA, "createPatient", keyDigest);
            waitForAdvisoryLockContention(expectedLockKey, 10);

            // 5. Only then release request 1
            releaseLatch.countDown();

            // 6. Collect both
            ResponseEntity<PatientResponse> res1 = future1.get(15, java.util.concurrent.TimeUnit.SECONDS);
            ResponseEntity<PatientResponse> res2 = future2.get(15, java.util.concurrent.TimeUnit.SECONDS);

            // B2(a): Concurrent identical bodies with same key: exactly two 201 responses with identical body and ETag
            // (a 409 must FAIL this test)
            assertThat(res1.getStatusCode()).as("Request 1 must succeed with 201 Created").isEqualTo(HttpStatus.CREATED);
            assertThat(res2.getStatusCode()).as("Request 2 must replay with 201 Created (409 must fail)").isEqualTo(HttpStatus.CREATED);

            assertThat(res1.getBody()).isNotNull();
            assertThat(res2.getBody()).isNotNull();
            assertThat(res1.getBody().id()).isEqualTo(res2.getBody().id());
            assertThat(res1.getBody().recordId()).isEqualTo(res2.getBody().recordId());
            assertThat(res1.getBody()).isEqualTo(res2.getBody());

            String etag1 = res1.getHeaders().getFirst(HttpHeaders.ETAG);
            String etag2 = res2.getHeaders().getFirst(HttpHeaders.ETAG);
            assertThat(etag1).isNotNull().isEqualTo(etag2);

            UUID createdPatientId = res1.getBody().id();

            // Exactly one row in each of the five tables for this patient / practice
            assertThat(countRowsInTableDirectly("patient", "id", createdPatientId)).isEqualTo(1);
            assertThat(countRowsInTableDirectly("patient_record", "patient_id", createdPatientId)).isEqualTo(1);
            assertThat(countRowsInTableDirectly("audit_event", "resource_id", createdPatientId)).isEqualTo(1);
            assertThat(countRowsInTableDirectly("outbox_event", "resource_id", createdPatientId)).isEqualTo(1);
            assertThat(countRowsInTableDirectly("idempotency_receipt", "response_resource", createdPatientId)).isEqualTo(1);

            assertThat(countRowsInTableDirectly("patient", "practice_id", practiceA)).isEqualTo(patientCountBefore + 1);
            assertThat(countRowsInTableDirectly("patient_record", "practice_id", practiceA)).isEqualTo(recordCountBefore + 1);
            assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isEqualTo(auditCountBefore + 1);
            assertThat(countRowsInTableDirectly("outbox_event", "practice_id", practiceA)).isEqualTo(outboxCountBefore + 1);
            assertThat(countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA)).isEqualTo(receiptCountBefore + 1);
        } finally {
            testFailureHelper.disablePauseInBeforeCommit();
            releaseLatch.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("C7: Scoped lock allows different key from same account to complete with 201 while request 1 is paused in beforeCommit")
    void scopedLock_differentKeyFromSameAccountCompletesWhileFirstPaused() throws Exception {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String key1 = "key-scoped-1-" + UUID.randomUUID();
        String key2 = "key-scoped-2-" + UUID.randomUUID();

        PatientEdit body1 = new PatientEdit(
                "PatientOne",
                "ScopedTest",
                LocalDate.of(1980, 1, 1),
                "patient1@example.com",
                "+33600000001"
        );

        PatientEdit body2 = new PatientEdit(
                "PatientTwo",
                "ScopedTest",
                LocalDate.of(1982, 2, 2),
                "patient2@example.com",
                "+33600000002"
        );

        CountDownLatch reachedLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        testFailureHelper.enablePauseInBeforeCommit(reachedLatch, releaseLatch);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);

        try {
            // Task 1 with key1
            java.util.concurrent.Callable<ResponseEntity<PatientResponse>> task1 = () -> client().post()
                    .uri("/api/v1/practices/" + practiceA + "/patients")
                    .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                    .header("X-CSRF-TOKEN", csrfToken)
                    .header("Idempotency-Key", key1)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body1)
                    .retrieve()
                    .onStatus(status -> status.is5xxServerError() || status.is4xxClientError(), (req, res) -> {})
                    .toEntity(PatientResponse.class);

            // Task 2 with key2 (different key from same account)
            java.util.concurrent.Callable<ResponseEntity<PatientResponse>> task2 = () -> client().post()
                    .uri("/api/v1/practices/" + practiceA + "/patients")
                    .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                    .header("X-CSRF-TOKEN", csrfToken)
                    .header("Idempotency-Key", key2)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body2)
                    .retrieve()
                    .onStatus(status -> status.is5xxServerError() || status.is4xxClientError(), (req, res) -> {})
                    .toEntity(PatientResponse.class);

            // 1. Submit request 1
            java.util.concurrent.Future<ResponseEntity<PatientResponse>> future1 = executor.submit(task1);

            // 2. Wait until request 1 reaches beforeCommit and pauses
            boolean reached = reachedLatch.await(15, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(reached).as("Request 1 must reach beforeCommit within 15 seconds").isTrue();

            // Verify request 1 holds its advisory lock in pg_locks
            String digest1 = idempotencyService.computeSha256(key1);
            long lockKey1 = idempotencyService.computeLockKey(practiceA, clinicalUserA, "createPatient", digest1);
            assertAdvisoryLockHeld(lockKey1);

            // 3. Submit request 2 and wait for it to complete WITHOUT releasing request 1
            // Because locks are fine-grained / scoped per key, request 2 must NOT block!
            java.util.concurrent.Future<ResponseEntity<PatientResponse>> future2 = executor.submit(task2);

            // Request 2 must complete within 10 seconds while request 1 is STILL PAUSED!
            ResponseEntity<PatientResponse> res2 = future2.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(res2.getBody()).isNotNull();
            assertThat(res2.getBody().firstName()).isEqualTo("PatientTwo");

            // 4. Now release request 1
            releaseLatch.countDown();

            // Request 1 completes
            ResponseEntity<PatientResponse> res1 = future1.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(res1.getBody()).isNotNull();
            assertThat(res1.getBody().firstName()).isEqualTo("PatientOne");

            // Both patients were created
            assertThat(res1.getBody().id()).isNotEqualTo(res2.getBody().id());
        } finally {
            testFailureHelper.disablePauseInBeforeCommit();
            releaseLatch.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("B2(b): Concurrent DIFFERENT valid bodies with same key give exactly one 201 and one 409 IDEMPOTENCY_CONFLICT, still one row per table")
    void concurrentDifferentBodiesWithSameKey_givesOneCreatedAndOneConflict() throws Exception {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);
        String idempotencyKey = "key-concurrent-diff-" + UUID.randomUUID();

        PatientEdit body1 = new PatientEdit(
                "Alpha",
                "ConcurrentDiff",
                LocalDate.of(1985, 3, 15),
                "alpha@example.com",
                "+33611223344"
        );

        PatientEdit body2 = new PatientEdit(
                "Beta",
                "ConcurrentDiff",
                LocalDate.of(1990, 6, 20),
                "beta@example.com",
                "+33699887766"
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
            // Task 1 with body1
            java.util.concurrent.Callable<ResponseEntity<PatientResponse>> task1 = () -> client().post()
                    .uri("/api/v1/practices/" + practiceA + "/patients")
                    .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                    .header("X-CSRF-TOKEN", csrfToken)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body1)
                    .retrieve()
                    .onStatus(status -> status.is5xxServerError() || status.is4xxClientError(), (req, res) -> {})
                    .toEntity(PatientResponse.class);

            // Task 2 with body2 (different body, same key)
            java.util.concurrent.Callable<ResponseEntity<Map>> task2 = () -> client().post()
                    .uri("/api/v1/practices/" + practiceA + "/patients")
                    .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                    .header("X-CSRF-TOKEN", csrfToken)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body2)
                    .retrieve()
                    .onStatus(status -> status.value() == 409, (req, res) -> {})
                    .toEntity(Map.class);

            // 1. Start request 1 on its own thread
            java.util.concurrent.Future<ResponseEntity<PatientResponse>> future1 = executor.submit(task1);

            // 2. Wait for its "reached beforeCommit" latch (bounded 15s)
            boolean reached = reachedLatch.await(15, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(reached).as("Request 1 must reach beforeCommit within 15 seconds").isTrue();

            // 3. Start request 2
            java.util.concurrent.Future<ResponseEntity<Map>> future2 = executor.submit(task2);

            // 4. B2(c): Poll pg_locks until advisory lock contention is observed
            String keyDigest = idempotencyService.computeSha256(idempotencyKey);
            long expectedLockKey = idempotencyService.computeLockKey(practiceA, clinicalUserA, "createPatient", keyDigest);
            waitForAdvisoryLockContention(expectedLockKey, 10);

            // 5. Release request 1 to commit
            releaseLatch.countDown();

            // 6. Collect both responses
            ResponseEntity<PatientResponse> res1 = future1.get(15, java.util.concurrent.TimeUnit.SECONDS);
            ResponseEntity<Map> res2 = future2.get(15, java.util.concurrent.TimeUnit.SECONDS);

            // Assert: exactly one 201 and one 409 IDEMPOTENCY_CONFLICT
            assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(res1.getBody()).isNotNull();

            assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(res2.getBody()).isNotNull();
            assertThat(res2.getBody().get("code")).isEqualTo("IDEMPOTENCY_CONFLICT");
            assertThat(res2.getBody().get("correlationId")).isNotNull();

            UUID createdPatientId = res1.getBody().id();

            // Still exactly one row in each of the five tables
            assertThat(countRowsInTableDirectly("patient", "id", createdPatientId)).isEqualTo(1);
            assertThat(countRowsInTableDirectly("patient_record", "patient_id", createdPatientId)).isEqualTo(1);
            assertThat(countRowsInTableDirectly("audit_event", "resource_id", createdPatientId)).isEqualTo(1);
            assertThat(countRowsInTableDirectly("outbox_event", "resource_id", createdPatientId)).isEqualTo(1);
            assertThat(countRowsInTableDirectly("idempotency_receipt", "response_resource", createdPatientId)).isEqualTo(1);

            assertThat(countRowsInTableDirectly("patient", "practice_id", practiceA)).isEqualTo(patientCountBefore + 1);
            assertThat(countRowsInTableDirectly("patient_record", "practice_id", practiceA)).isEqualTo(recordCountBefore + 1);
            assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isEqualTo(auditCountBefore + 1);
            assertThat(countRowsInTableDirectly("outbox_event", "practice_id", practiceA)).isEqualTo(outboxCountBefore + 1);
            assertThat(countRowsInTableDirectly("idempotency_receipt", "practice_id", practiceA)).isEqualTo(receiptCountBefore + 1);
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
                LocalDate.now().plusYears(1),
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

        String malformedJson = "{ \"firstName\": \"Jean\", \"lastName\": \"Dupont\", \"dateOfBirth\": \"1990-0";

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
    @DisplayName("A8: Strict typing: JSON number or boolean for string fields gives 400 MALFORMED_REQUEST")
    void strictTyping_numberOrBooleanForStringFields_returns400MalformedRequest() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);

        List<String> payloads = List.of(
                "{\"firstName\": 12345, \"lastName\": \"Martin\", \"dateOfBirth\": \"1990-01-01\"}",
                "{\"firstName\": true, \"lastName\": \"Martin\", \"dateOfBirth\": \"1990-01-01\"}",
                "{\"firstName\": \"Alice\", \"lastName\": 9876, \"dateOfBirth\": \"1990-01-01\"}",
                "{\"firstName\": \"Alice\", \"lastName\": false, \"dateOfBirth\": \"1990-01-01\"}",
                "{\"firstName\": \"Alice\", \"lastName\": \"Martin\", \"dateOfBirth\": \"1990-01-01\", \"contactEmail\": 123}",
                "{\"firstName\": \"Alice\", \"lastName\": \"Martin\", \"dateOfBirth\": \"1990-01-01\", \"contactEmail\": true}",
                "{\"firstName\": \"Alice\", \"lastName\": \"Martin\", \"dateOfBirth\": \"1990-01-01\", \"phone\": 123456}",
                "{\"firstName\": \"Alice\", \"lastName\": \"Martin\", \"dateOfBirth\": \"1990-01-01\", \"phone\": false}"
        );

        for (String payload : payloads) {
            String idempotencyKey = "key-strict-str-" + UUID.randomUUID();
            ResponseEntity<Map> response = client().post()
                    .uri("/api/v1/practices/" + practiceA + "/patients")
                    .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                    .header("X-CSRF-TOKEN", csrfToken)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(status -> status.value() == 400, (req, res) -> {})
                    .toEntity(Map.class);

            assertThat(response.getStatusCode()).as("Payload: " + payload).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("code")).as("Payload: " + payload).isEqualTo("MALFORMED_REQUEST");
        }
    }

    @Test
    @DisplayName("C4: Strict date validation for dateOfBirth field only")
    void c4_strictDateValidation_dateOfBirthOnly() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);

        List<String> invalidDates = List.of(
                " 1990-01-01",
                "1990-01-01 ",
                "+19900-01-01",
                "1990-1-1",
                "1990-02-30"
        );

        for (String invalidDate : invalidDates) {
            String payload = String.format("{\"firstName\": \"Alice\", \"lastName\": \"Martin\", \"dateOfBirth\": \"%s\"}", invalidDate);
            String idempotencyKey = "key-strict-date-" + UUID.randomUUID();

            ResponseEntity<Map> response = client().post()
                    .uri("/api/v1/practices/" + practiceA + "/patients")
                    .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                    .header("X-CSRF-TOKEN", csrfToken)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(status -> status.value() == 400 || status.value() == 422, (req, res) -> {})
                    .toEntity(Map.class);

            assertThat(response.getStatusCode())
                    .as("Date '%s' must give 400 Bad Request", invalidDate)
                    .isEqualTo(HttpStatus.BAD_REQUEST);

            Map<?, ?> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("code"))
                    .as("Date '%s' must have code MALFORMED_REQUEST", invalidDate)
                    .isEqualTo("MALFORMED_REQUEST");

            // Response must NOT contain the submitted value
            assertThat(body.toString()).doesNotContain(invalidDate);
        }

        // Positive case: "1990-01-01" still gives 201 Created
        String validPayload = "{\"firstName\": \"Alice\", \"lastName\": \"Martin\", \"dateOfBirth\": \"1990-01-01\"}";
        String validIdempKey = "key-strict-date-valid-" + UUID.randomUUID();
        ResponseEntity<PatientResponse> validResponse = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", validIdempKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(validPayload)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (req, res) -> {})
                .toEntity(PatientResponse.class);

        assertThat(validResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("A8: Strict typing: non-string value for dateOfBirth (number, array, object) gives 400 MALFORMED_REQUEST")
    void strictTyping_nonStringForDateOfBirth_returns400MalformedRequest() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);

        List<String> payloads = List.of(
                "{\"firstName\": \"Alice\", \"lastName\": \"Martin\", \"dateOfBirth\": [1990, 1, 1]}",
                "{\"firstName\": \"Alice\", \"lastName\": \"Martin\", \"dateOfBirth\": 123456789}",
                "{\"firstName\": \"Alice\", \"lastName\": \"Martin\", \"dateOfBirth\": {\"year\": 1990, \"month\": 1, \"day\": 1}}",
                "{\"firstName\": \"Alice\", \"lastName\": \"Martin\", \"dateOfBirth\": true}"
        );

        for (String payload : payloads) {
            String idempotencyKey = "key-strict-dob-" + UUID.randomUUID();
            ResponseEntity<Map> response = client().post()
                    .uri("/api/v1/practices/" + practiceA + "/patients")
                    .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                    .header("X-CSRF-TOKEN", csrfToken)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(status -> status.value() == 400, (req, res) -> {})
                    .toEntity(Map.class);

            assertThat(response.getStatusCode()).as("Payload: " + payload).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("code")).as("Payload: " + payload).isEqualTo("MALFORMED_REQUEST");
        }
    }

    @Test
    @DisplayName("A8: Strict typing: contactEmail present but empty gives 400 VALIDATION_FAILED with field error; phone empty string is valid")
    void strictTyping_emptyContactEmailFails_emptyPhoneValid() {
        String csrfToken = fetchCsrfToken(sessionCookieClinicalA);

        // 1. contactEmail empty string gives 400 VALIDATION_FAILED with field error
        String payloadEmptyEmail = """
                {"firstName": "Alice", "lastName": "Martin", "dateOfBirth": "1990-01-01", "contactEmail": ""}
                """;
        ResponseEntity<Map> emailResponse = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", "key-empty-email-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payloadEmptyEmail)
                .retrieve()
                .onStatus(status -> status.value() == 400, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(emailResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(emailResponse.getBody().get("code")).isEqualTo("VALIDATION_FAILED");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) emailResponse.getBody().get("fieldErrors");
        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(err -> "contactEmail".equals(err.get("field")));

        // 2. phone empty string is valid and returns 201 Created
        String payloadEmptyPhone = """
                {"firstName": "Alice", "lastName": "Martin", "dateOfBirth": "1990-01-01", "phone": ""}
                """;
        ResponseEntity<PatientResponse> phoneResponse = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", "key-empty-phone-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payloadEmptyPhone)
                .retrieve()
                .toEntity(PatientResponse.class);

        assertThat(phoneResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(phoneResponse.getBody().phone()).isEqualTo("");
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
        assertThat(response.getHeaders().get(HttpHeaders.ALLOW)).isNotNull().isNotEmpty();
        assertThat(response.getHeaders().getFirst(HttpHeaders.ALLOW)).contains("POST");
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
    @DisplayName("B6: Replay after membership suspension gives 403 and commits only its security audit event")
    void b6_replayAfterMembershipSuspended_isDeniedWithOnlySecurityAudit() throws Exception {
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

        String correlationId = assertDenialProblem(replayResponse, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
        assertDenialEvent(practiceA, operatorAccount, correlationId,
                Map.of("reason", "MEMBERSHIP_NOT_ACTIVE", "operation", CREATE_OPERATION, "capability", "CLINICAL_WRITE"));
        assertThat(countRowsInTableDirectly("patient", "practice_id", practiceA)).isEqualTo(patientCount);
        assertThat(countRowsInTableDirectly("patient_record", "practice_id", practiceA)).isEqualTo(recordCount);
        assertThat(countRowsInTableDirectly("audit_event", "practice_id", practiceA)).isEqualTo(auditCount + 1);
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
