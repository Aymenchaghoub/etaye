package dev.aymen.etaye.records;

import dev.aymen.etaye.TestSessionHelper;
import dev.aymen.etaye.isolation.IsolationMatrix;
import dev.aymen.etaye.records.dto.PatientEdit;
import dev.aymen.etaye.records.dto.PatientResponse;
import dev.aymen.etaye.records.dto.RecordResponse;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@org.junit.jupiter.api.extension.ExtendWith(IsolationMatrix.Extension.class)
public class CarrierRouteOutageTest {

    private static final String OWNER_USER = "etaye_owner";
    private static final String OWNER_PASSWORD = UUID.randomUUID().toString();
    private static final String RUNTIME_USER = "etaye_runtime";
    private static final String RUNTIME_PASSWORD = UUID.randomUUID().toString();
    private static final String IDENTITY_USER = "etaye_identity";
    private static final String IDENTITY_PASSWORD = UUID.randomUUID().toString();
    private static final String ADMIN_PASSWORD = UUID.randomUUID().toString();

    private static final PostgreSQLContainer<?> OUTAGE_CONTAINER;

    static {
        OUTAGE_CONTAINER = new PostgreSQLContainer<>("postgres:18")
                .withDatabaseName("etaye_carrier_outage_test")
                .withUsername("postgres")
                .withPassword(ADMIN_PASSWORD);
        OUTAGE_CONTAINER.start();

        provisionRolesAndDatabase();
        runFlywayAsOwner();
    }

    private static void provisionRolesAndDatabase() {
        try (Connection conn = DriverManager.getConnection(
                OUTAGE_CONTAINER.getJdbcUrl(),
                OUTAGE_CONTAINER.getUsername(),
                OUTAGE_CONTAINER.getPassword());
             Statement stmt = conn.createStatement()) {

            String sql = """
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '%s') THEN
                        CREATE ROLE %s WITH LOGIN PASSWORD '%s' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
                    END IF;
                    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '%s') THEN
                        CREATE ROLE %s WITH LOGIN PASSWORD '%s' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
                    END IF;
                    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '%s') THEN
                        CREATE ROLE %s WITH LOGIN PASSWORD '%s' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
                    END IF;
                END
                $$;

                GRANT CONNECT ON DATABASE etaye_carrier_outage_test TO %s, %s, %s;
                ALTER SCHEMA public OWNER TO %s;
                GRANT ALL ON SCHEMA public TO %s;
                GRANT USAGE ON SCHEMA public TO %s, %s;
                """.formatted(
                    OWNER_USER, OWNER_USER, OWNER_PASSWORD,
                    RUNTIME_USER, RUNTIME_USER, RUNTIME_PASSWORD,
                    IDENTITY_USER, IDENTITY_USER, IDENTITY_PASSWORD,
                    OWNER_USER, RUNTIME_USER, IDENTITY_USER,
                    OWNER_USER,
                    OWNER_USER,
                    RUNTIME_USER, IDENTITY_USER
            );

            stmt.execute(sql);
        } catch (Exception e) {
            throw new RuntimeException("Failed to provision carrier outage test database roles", e);
        }
    }

    private static void runFlywayAsOwner() {
        Flyway flyway = Flyway.configure()
                .dataSource(OUTAGE_CONTAINER.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", OUTAGE_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", () -> RUNTIME_USER);
        registry.add("spring.datasource.password", () -> RUNTIME_PASSWORD);
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("etaye.identity.datasource.username", () -> IDENTITY_USER);
        registry.add("etaye.identity.datasource.password", () -> IDENTITY_PASSWORD);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestSessionHelper testSessionHelper;

    private UUID createPractice(String name) {
        try (Connection conn = DriverManager.getConnection(
                OUTAGE_CONTAINER.getJdbcUrl(),
                OWNER_USER,
                OWNER_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO practice (display_name, timezone) VALUES (?, 'Europe/Paris') RETURNING id")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("id");
                }
            }
            throw new IllegalStateException("Failed to retrieve generated practice id");
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert practice fixture as owner in carrier outage DB", e);
        }
    }

    private UUID createAccount(String email, String displayName) {
        try (Connection conn = DriverManager.getConnection(
                OUTAGE_CONTAINER.getJdbcUrl(),
                OWNER_USER,
                OWNER_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO account (normalized_email, display_name, password_hash) VALUES (?, ?, ?) RETURNING id")) {
            ps.setString(1, email);
            ps.setString(2, displayName);
            ps.setString(3, "$argon2id$v=19$m=65536,t=3,p=4$" + UUID.randomUUID() + "$" + UUID.randomUUID());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("id");
                }
            }
            throw new IllegalStateException("Failed to retrieve generated account id");
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert account fixture as owner in carrier outage DB", e);
        }
    }

    private void createMembership(UUID accountId, UUID practiceId, String[] capabilities) {
        try (Connection conn = DriverManager.getConnection(
                OUTAGE_CONTAINER.getJdbcUrl(),
                OWNER_USER,
                OWNER_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO membership (account_id, practice_id, state, capabilities) VALUES (?, ?, 'ACTIVE', ?) RETURNING id")) {
            ps.setObject(1, accountId);
            ps.setObject(2, practiceId);
            ps.setArray(3, conn.createArrayOf("text", capabilities));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
            throw new IllegalStateException("Failed to retrieve generated membership id");
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert membership fixture as owner in carrier outage DB", e);
        }
    }

    private RestClient client() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(requestFactory)
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
    @DisplayName("Carrier route responds 503 SERVICE_UNAVAILABLE under paused and stopped database, and recovers to 200")
    void carrierRouteOutageReturnsServiceUnavailableAndRecovers() {
        UUID practiceId = createPractice("Outage Practice " + UUID.randomUUID());
        UUID accountId = createAccount("clinician.outage." + UUID.randomUUID() + "@example.com", "Dr Outage");
        createMembership(accountId, practiceId, new String[]{"CLINICAL_WRITE", "CLINICAL_READ"});

        String sessionCookie = testSessionHelper.createSessionCookie(accountId);
        String csrfToken = fetchCsrfToken(sessionCookie);

        // 1. Create a patient to obtain a valid recordId while the database is UP
        PatientEdit edit = new PatientEdit(
                "Claire",
                "Delacroix",
                LocalDate.of(1990, 8, 15),
                "claire.delacroix@example.com",
                "+33198765432"
        );
        String idempotencyKey = "key-" + UUID.randomUUID();

        ResponseEntity<PatientResponse> createResponse = client().post()
                .uri("/api/v1/practices/" + practiceId + "/patients")
                .header(HttpHeaders.COOKIE, sessionCookie)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(edit)
                .retrieve()
                .toEntity(PatientResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PatientResponse patient = createResponse.getBody();
        assertThat(patient).isNotNull();
        UUID recordId = patient.recordId();
        assertThat(recordId).isNotNull();

        // 2. Positive control: GET /records/{recordId} returns 200 OK
        ResponseEntity<RecordResponse> initialRecord = client().get()
                .uri("/api/v1/practices/" + practiceId + "/records/" + recordId)
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve()
                .toEntity(RecordResponse.class);

        assertThat(initialRecord.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initialRecord.getBody()).isNotNull();
        assertThat(initialRecord.getBody().id()).isEqualTo(recordId);

        // 3. Pause container to simulate silent database outage
        String containerId = OUTAGE_CONTAINER.getContainerId();
        OUTAGE_CONTAINER.getDockerClient().pauseContainerCmd(containerId).exec();

        java.util.concurrent.atomic.AtomicReference<String> failure = new java.util.concurrent.atomic.AtomicReference<>();
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                long startPause = System.currentTimeMillis();
                ResponseEntity<Map> pausedResponse = client().get()
                        .uri("/api/v1/practices/" + practiceId + "/records/" + recordId)
                        .header(HttpHeaders.COOKIE, sessionCookie)
                        .retrieve()
                        .onStatus(status -> status.is5xxServerError(), (req, res) -> {})
                        .toEntity(Map.class);
                long pauseDuration = System.currentTimeMillis() - startPause;

                assertThat(pausedResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(pausedResponse.getHeaders().getContentType().toString()).contains("application/problem+json");
                Map<?, ?> body = pausedResponse.getBody();
                assertThat(body).isNotNull();
                assertThat(body.get("code")).isEqualTo("SERVICE_UNAVAILABLE");
                assertThat(body.get("status")).isEqualTo(503);
                assertThat(pauseDuration).isLessThan(4500L);
                failure.set(IsolationMatrix.outcome(pausedResponse.getStatusCode().value(), body.get("code")));
            });
        } finally {
            // Unpause container to restore database connectivity
            OUTAGE_CONTAINER.getDockerClient().unpauseContainerCmd(containerId).exec();
        }

        IsolationMatrix.observe("TX-DEPENDENCY-FAILURE", failure.get());

        // 4. Positive recovery: verify 200 OK after unpause
        ResponseEntity<RecordResponse> recoveredRecord = client().get()
                .uri("/api/v1/practices/" + practiceId + "/records/" + recordId)
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve()
                .toEntity(RecordResponse.class);

        assertThat(recoveredRecord.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recoveredRecord.getBody()).isNotNull();
        assertThat(recoveredRecord.getBody().id()).isEqualTo(recordId);
        IsolationMatrix.observe("TX-DEPENDENCY-RECOVERY", IsolationMatrix.outcome(recoveredRecord.getStatusCode().value(), null));

        // 5. Stop container to simulate total database crash
        OUTAGE_CONTAINER.stop();

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            long startStop = System.currentTimeMillis();
            ResponseEntity<Map> stoppedResponse = client().get()
                    .uri("/api/v1/practices/" + practiceId + "/records/" + recordId)
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .retrieve()
                    .onStatus(status -> status.is5xxServerError(), (req, res) -> {})
                    .toEntity(Map.class);
            long stopDuration = System.currentTimeMillis() - startStop;

            assertThat(stoppedResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(stoppedResponse.getHeaders().getContentType().toString()).contains("application/problem+json");
            Map<?, ?> body = stoppedResponse.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("code")).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(body.get("status")).isEqualTo(503);
            assertThat(stopDuration).isLessThan(4500L);
        });

        // 6. Verify POST /patients also returns 503 SERVICE_UNAVAILABLE under stopped container
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            long startPost = System.currentTimeMillis();
            ResponseEntity<Map> stoppedPostResponse = client().post()
                    .uri("/api/v1/practices/" + practiceId + "/patients")
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .header("X-CSRF-TOKEN", csrfToken)
                    .header("Idempotency-Key", "key-outage-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(edit)
                    .retrieve()
                    .onStatus(status -> status.is5xxServerError(), (req, res) -> {})
                    .toEntity(Map.class);
            long postDuration = System.currentTimeMillis() - startPost;

            assertThat(stoppedPostResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(stoppedPostResponse.getHeaders().getContentType().toString()).contains("application/problem+json");
            Map<?, ?> body = stoppedPostResponse.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("code")).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(body.get("status")).isEqualTo(503);
            assertThat(postDuration).isLessThan(4500L);
        });
    }
}
