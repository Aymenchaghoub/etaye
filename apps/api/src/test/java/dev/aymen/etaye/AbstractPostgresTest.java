package dev.aymen.etaye;

import org.flywaydb.core.Flyway;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

@ActiveProfiles("test")
public abstract class AbstractPostgresTest {

    protected static final String OWNER_USER = "etaye_owner";
    protected static final String OWNER_PASSWORD = UUID.randomUUID().toString();
    protected static final String RUNTIME_USER = "etaye_runtime";
    protected static final String RUNTIME_PASSWORD = UUID.randomUUID().toString();
    protected static final String IDENTITY_USER = "etaye_identity";
    protected static final String IDENTITY_PASSWORD = UUID.randomUUID().toString();
    private static final String ADMIN_PASSWORD = UUID.randomUUID().toString();

    protected static final PostgreSQLContainer<?> POSTGRES_CONTAINER;

    static {
        POSTGRES_CONTAINER = new PostgreSQLContainer<>("postgres:18")
                .withDatabaseName("etaye_test")
                .withUsername("postgres")
                .withPassword(ADMIN_PASSWORD);
        POSTGRES_CONTAINER.start();

        provisionRolesAndDatabase();
        runFlywayAsOwner();
    }

    private static void provisionRolesAndDatabase() {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(),
                POSTGRES_CONTAINER.getUsername(),
                POSTGRES_CONTAINER.getPassword());
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

                GRANT CONNECT ON DATABASE etaye_test TO %s, %s, %s;
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
            throw new RuntimeException("Failed to provision PostgreSQL test roles", e);
        }
    }

    private static void runFlywayAsOwner() {
        // Flyway executes exclusively under the owner credentials before the application runtime starts
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES_CONTAINER.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
    }

    protected static UUID createPracticeAsOwner(String name) {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(),
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
            throw new RuntimeException("Failed to insert practice fixture as owner", e);
        }
    }

    protected static UUID createAccountAsOwner(String email, String displayName, String passwordHash) {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(),
                OWNER_USER,
                OWNER_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO account (normalized_email, display_name, password_hash) VALUES (?, ?, ?) RETURNING id")) {
            ps.setString(1, email);
            ps.setString(2, displayName);
            ps.setString(3, passwordHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("id");
                }
            }
            throw new IllegalStateException("Failed to retrieve generated account id");
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert account fixture as owner", e);
        }
    }

    protected static UUID createMembershipAsOwner(UUID accountId, UUID practiceId, String state, String[] capabilities) {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(),
                OWNER_USER,
                OWNER_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO membership (account_id, practice_id, state, capabilities) VALUES (?, ?, ?, ?) RETURNING id")) {
            ps.setObject(1, accountId);
            ps.setObject(2, practiceId);
            ps.setString(3, state);
            ps.setArray(4, conn.createArrayOf("text", capabilities));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("id");
                }
            }
            throw new IllegalStateException("Failed to retrieve generated membership id");
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert membership fixture as owner", e);
        }
    }

    protected static void updateMembershipStateAsOwner(UUID accountId, UUID practiceId, String newState) {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(),
                OWNER_USER,
                OWNER_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE membership SET state = ? WHERE account_id = ? AND practice_id = ?")) {
            ps.setString(1, newState);
            ps.setObject(2, accountId);
            ps.setObject(3, practiceId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalStateException("No membership row updated for account " + accountId + " and practice " + practiceId);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update membership state as owner", e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // HTTP application datasource connects strictly under the non-owner runtime role
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", () -> RUNTIME_USER);
        registry.add("spring.datasource.password", () -> RUNTIME_PASSWORD);
        registry.add("spring.flyway.enabled", () -> "false");

        // Identity datasource connects strictly under the restricted etaye_identity role
        registry.add("etaye.identity.datasource.username", () -> IDENTITY_USER);
        registry.add("etaye.identity.datasource.password", () -> IDENTITY_PASSWORD);
    }
}
