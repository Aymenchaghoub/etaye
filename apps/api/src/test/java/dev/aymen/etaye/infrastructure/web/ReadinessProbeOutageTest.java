package dev.aymen.etaye.infrastructure.web;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ReadinessProbeOutageTest {

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
                .withDatabaseName("etaye_outage_test")
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

                GRANT CONNECT ON DATABASE etaye_outage_test TO %s, %s, %s;
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
            throw new RuntimeException("Failed to provision outage test database roles", e);
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
        registry.add("etaye.identity.datasource.username", () -> IDENTITY_USER);
        registry.add("etaye.identity.datasource.password", () -> IDENTITY_PASSWORD);
        // Production timeouts are used directly from DataSourceConfig / application.yml
        // No test-only timeout overrides (B7)
    }

    @LocalServerPort
    private int port;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    @DisplayName("B7: Readiness probe outage test under production timeouts with paused and stopped container")
    void readinessProbeFailsWithinAboutFourSecondsUnderOutages() {
        // 1. Initial state: Database is UP, probe returns 200 OK with only {"status":"UP"}
        ResponseEntity<Map> upResponse = client().get()
                .uri("/api/v1/probes/readiness")
                .retrieve()
                .toEntity(Map.class);

        assertThat(upResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(upResponse.getBody()).containsExactly(Map.entry("status", "UP"));

        // 2. Pause the container to simulate silent network / database loss (docker pause)
        String containerId = OUTAGE_CONTAINER.getContainerId();
        OUTAGE_CONTAINER.getDockerClient().pauseContainerCmd(containerId).exec();

        try {
            long startPause = System.currentTimeMillis();
            ResponseEntity<Map> pausedResponse = client().get()
                    .uri("/api/v1/probes/readiness")
                    .retrieve()
                    .onStatus(status -> status.value() == 503, (req, res) -> {})
                    .toEntity(Map.class);
            long pauseDuration = System.currentTimeMillis() - startPause;

            assertThat(pausedResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(pausedResponse.getBody()).containsExactly(Map.entry("status", "DOWN"));
            assertThat(pauseDuration).isLessThan(4500L);
        } finally {
            // Unpause to restore connectivity before stopped test
            OUTAGE_CONTAINER.getDockerClient().unpauseContainerCmd(containerId).exec();
        }

        // Verify recovery after unpausing
        ResponseEntity<Map> recoveredResponse = client().get()
                .uri("/api/v1/probes/readiness")
                .retrieve()
                .toEntity(Map.class);
        assertThat(recoveredResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recoveredResponse.getBody()).containsExactly(Map.entry("status", "UP"));

        // 3. Stop the container to simulate crash outage
        OUTAGE_CONTAINER.stop();

        long startStop = System.currentTimeMillis();
        ResponseEntity<Map> stoppedResponse = client().get()
                .uri("/api/v1/probes/readiness")
                .retrieve()
                .onStatus(status -> status.value() == 503, (req, res) -> {})
                .toEntity(Map.class);
        long stopDuration = System.currentTimeMillis() - startStop;

        assertThat(stoppedResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(stoppedResponse.getBody()).containsExactly(Map.entry("status", "DOWN"));
        assertThat(stopDuration).isLessThan(4500L);
    }
}
