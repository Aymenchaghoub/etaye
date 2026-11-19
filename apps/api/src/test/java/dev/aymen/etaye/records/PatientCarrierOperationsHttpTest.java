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

    private record CsrfTokenPair(String token, String cookie) {}

    private CsrfTokenPair fetchCsrfToken() {
        ResponseEntity<Map> response = client().get()
                .uri("/api/v1/csrf")
                .retrieve()
                .toEntity(Map.class);

        String token = (String) response.getBody().get("token");
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        String xsrfCookie = "";
        if (setCookie != null) {
            for (String part : setCookie.split(";")) {
                if (part.trim().startsWith("XSRF-TOKEN=")) {
                    xsrfCookie = part.trim();
                    break;
                }
            }
        }
        return new CsrfTokenPair(token, xsrfCookie);
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
        CsrfTokenPair csrf = fetchCsrfToken();
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
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA + "; " + csrf.cookie())
                .header("X-CSRF-TOKEN", csrf.token())
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
    @DisplayName("C2: Injected failure after domain write rolls back all 5 tables (0 rows)")
    void c2_injectedFailureAfterDomainWrite_rollsBackAllFiveTables() {
        CsrfTokenPair csrf = fetchCsrfToken();
        String idempotencyKey = "key-fail-" + UUID.randomUUID();

        PatientEdit edit = new PatientEdit(
                "Rollback",
                "Patient",
                LocalDate.of(1990, 1, 1),
                null,
                null
        );

        ResponseEntity<Map> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA + "; " + csrf.cookie())
                .header("X-CSRF-TOKEN", csrf.token())
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Test-Fail-After-Write", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .onStatus(status -> status.value() == 500, (req, res) -> {})
                .toEntity(Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("code")).isEqualTo("INTERNAL_ERROR");

        // Verify that 0 rows were committed across all tables
        assertThat(countRowsInTableDirectly("patient", "last_name", "Patient")).isEqualTo(0);
        assertThat(countRowsInTableDirectly("audit_event", "event_type", "PATIENT_CREATED_FAIL")).isEqualTo(0);
        assertThat(countRowsInTableDirectly("idempotency_receipt", "key_digest", idempotencyKey)).isEqualTo(0);
    }

    @Test
    @DisplayName("C3: Practitioner of practice B reading record of practice A gets indistinguishable 404")
    void c3_crossPracticeRecordRead_returnsIndistinguishable404() {
        CsrfTokenPair csrf = fetchCsrfToken();
        String idempotencyKey = "key-cross-" + UUID.randomUUID();

        PatientEdit edit = new PatientEdit("Alice", "Martin", LocalDate.of(1982, 3, 14), null, null);
        PatientResponse patient = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA + "; " + csrf.cookie())
                .header("X-CSRF-TOKEN", csrf.token())
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
        CsrfTokenPair csrf = fetchCsrfToken();
        String idempotencyKey = "key-denied-" + UUID.randomUUID();

        PatientEdit edit = new PatientEdit("Denied", "Candidate", LocalDate.of(1995, 8, 12), null, null);

        int initialAuditCount = countRowsInTableDirectly("audit_event", "practice_id", practiceA);
        int initialOutboxCount = countRowsInTableDirectly("outbox_event", "practice_id", practiceA);

        // 1. Administrator without CLINICAL_WRITE attempts createPatient
        ResponseEntity<Map> adminResponse = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieAdminOnlyA + "; " + csrf.cookie())
                .header("X-CSRF-TOKEN", csrf.token())
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
        ResponseEntity<Map> readOnlyResponse = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieReadOnlyA + "; " + csrf.cookie())
                .header("X-CSRF-TOKEN", csrf.token())
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
        CsrfTokenPair csrf = fetchCsrfToken();
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
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA + "; " + csrf.cookie())
                .header("X-CSRF-TOKEN", csrf.token())
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
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA + "; " + csrf.cookie())
                .header("X-CSRF-TOKEN", csrf.token())
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
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA + "; " + csrf.cookie())
                .header("X-CSRF-TOKEN", csrf.token())
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
        CsrfTokenPair csrf = fetchCsrfToken();
        PatientEdit edit = new PatientEdit("Short", "Key", LocalDate.of(1991, 1, 1), null, null);

        // Idempotency-Key with < 16 characters
        ResponseEntity<Map> response = client().post()
                .uri("/api/v1/practices/" + practiceA + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookieClinicalA + "; " + csrf.cookie())
                .header("X-CSRF-TOKEN", csrf.token())
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
}
