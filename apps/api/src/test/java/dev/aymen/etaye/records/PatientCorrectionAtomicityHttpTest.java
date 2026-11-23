package dev.aymen.etaye.records;

import dev.aymen.etaye.AbstractPostgresTest;
import dev.aymen.etaye.TestSessionHelper;
import dev.aymen.etaye.audit.service.AuditService;
import dev.aymen.etaye.communications.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BR-017 for the correction path: when the audit row or the outbox row cannot be written, the identity,
 * the patient version and the record version stay exactly as they were and no event row remains.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PatientCorrectionAtomicityHttpTest.FaultConfig.class)
public class PatientCorrectionAtomicityHttpTest extends AbstractPostgresTest {

    static volatile boolean failAudit;
    static volatile boolean failOutbox;

    @TestConfiguration(proxyBeanMethods = false)
    static class FaultConfig {
        @Bean
        @Primary
        AuditService faultyAudit(JdbcTemplate jdbcTemplate) {
            return new AuditService(jdbcTemplate) {
                @Override
                public void recordEvent(UUID practiceId, UUID actorAccountId, String eventType, String resourceType,
                                        UUID resourceId, Long resourceVersion, String outcome, String correlationId,
                                        String safeMetadataJson) {
                    if (failAudit && "PATIENT_UPDATED".equals(eventType)) {
                        throw new IllegalStateException("synthetic audit failure");
                    }
                    super.recordEvent(practiceId, actorAccountId, eventType, resourceType, resourceId, resourceVersion,
                            outcome, correlationId, safeMetadataJson);
                }
            };
        }

        @Bean
        @Primary
        OutboxService faultyOutbox(JdbcTemplate jdbcTemplate) {
            return new OutboxService(jdbcTemplate) {
                @Override
                public void publish(UUID practiceId, String eventType, UUID resourceId, Long resourceVersion,
                                    String dedupeKey, String safePayloadJson) {
                    if (failOutbox && "PATIENT_UPDATED".equals(eventType)) {
                        throw new IllegalStateException("synthetic outbox failure");
                    }
                    super.publish(practiceId, eventType, resourceId, resourceVersion, dedupeKey, safePayloadJson);
                }
            };
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestSessionHelper sessions;

    private UUID practice;
    private String cookie;

    @BeforeEach
    void fixtures() {
        failAudit = false;
        failOutbox = false;
        practice = createPracticeAsOwner("Cabinet atomicité " + UUID.randomUUID());
        UUID account = createAccountAsOwner("atomic." + UUID.randomUUID() + "@example.test", "Dr Atomique");
        createMembershipAsOwner(account, practice, "ACTIVE", new String[]{"CLINICAL_READ", "CLINICAL_WRITE"});
        cookie = sessions.createSessionCookie(account);
    }

    private RestClient client() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> call(HttpMethod method, String uri, Map<String, ?> body, Map<String, String> headers) {
        String token = (String) client().get().uri("/api/v1/csrf").header(HttpHeaders.COOKIE, cookie).retrieve().toEntity(Map.class).getBody().get("token");
        RestClient.RequestBodySpec spec = client().method(method).uri(uri).header(HttpHeaders.COOKIE, cookie)
                .header("X-CSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON);
        headers.forEach(spec::header);
        return (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) spec.body(body).retrieve().onStatus(s -> true, (rq, rs) -> {}).toEntity(Map.class);
    }

    private String value(String sql) {
        try (Connection c = DriverManager.getConnection(POSTGRES_CONTAINER.getJdbcUrl(), POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword());
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void anAuditOrOutboxFailureLeavesTheCorrectionUndone() {
        Map<String, Object> created = call(HttpMethod.POST, "/api/v1/practices/" + practice + "/patients",
                Map.of("firstName", "Jeanne", "lastName", "Dupont", "dateOfBirth", "1985-06-02"),
                Map.of("Idempotency-Key", "key-" + UUID.randomUUID())).getBody();
        String id = (String) created.get("id");
        String record = (String) created.get("recordId");
        String uri = "/api/v1/practices/" + practice + "/patients/" + id;
        Map<String, Object> correction = Map.of("firstName", "Jeanne-Marie", "lastName", "Dupont", "dateOfBirth", "1985-06-02");
        String recordVersionBefore = value("SELECT version FROM patient_record WHERE id = '" + record + "'");

        for (String fault : new String[]{"audit", "outbox"}) {
            failAudit = fault.equals("audit");
            failOutbox = fault.equals("outbox");
            assertThat(call(HttpMethod.PATCH, uri, correction, Map.of("If-Match", "\"0\"")).getStatusCode().value())
                    .as(fault + " failure surfaces as a server error, never a success").isEqualTo(500);
            assertThat(value("SELECT first_name || ':' || version FROM patient WHERE id = '" + id + "'")).isEqualTo("Jeanne:0");
            assertThat(value("SELECT version FROM patient_record WHERE id = '" + record + "'")).isEqualTo(recordVersionBefore);
            assertThat(value("SELECT count(*) FROM audit_event WHERE event_type = 'PATIENT_UPDATED' AND resource_id = '" + id + "'")).isEqualTo("0");
            assertThat(value("SELECT count(*) FROM outbox_event WHERE event_type = 'PATIENT_UPDATED' AND resource_id = '" + id + "'")).isEqualTo("0");
        }

        failAudit = false;
        failOutbox = false;
        assertThat(call(HttpMethod.PATCH, uri, correction, Map.of("If-Match", "\"0\"")).getStatusCode().value()).isEqualTo(200);
        assertThat(value("SELECT first_name || ':' || version FROM patient WHERE id = '" + id + "'")).isEqualTo("Jeanne-Marie:1");
        assertThat(value("SELECT count(*) FROM audit_event WHERE event_type = 'PATIENT_UPDATED' AND resource_id = '" + id + "'")).isEqualTo("1");
    }
}
