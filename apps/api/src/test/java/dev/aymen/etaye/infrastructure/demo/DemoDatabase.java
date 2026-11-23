package dev.aymen.etaye.infrastructure.demo;

import dev.aymen.etaye.AbstractPostgresTest;
import org.flywaydb.core.Flyway;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * The dedicated {@code etaye_demo} database of the synthetic package, created once per test JVM next to the
 * ordinary test database so that every demo-backed suite shares the same provisioned fixture.
 */
public final class DemoDatabase extends AbstractPostgresTest {
    private static final String URL = ordinaryUrl().replace("/etaye_test", "/etaye_demo");

    static {
        try (var admin = DriverManager.getConnection(ordinaryUrl(), "postgres", POSTGRES_CONTAINER.getPassword());
             var command = admin.createStatement()) {
            command.execute("CREATE DATABASE etaye_demo");
        } catch (SQLException ex) {
            throw new ExceptionInInitializerError(ex);
        }
        try (var admin = DriverManager.getConnection(URL, "postgres", POSTGRES_CONTAINER.getPassword());
             var command = admin.createStatement()) {
            command.execute("""
                    ALTER SCHEMA public OWNER TO etaye_owner;
                    GRANT USAGE ON SCHEMA public TO etaye_runtime, etaye_identity;
                    CREATE SCHEMA etaye_demo_bootstrap AUTHORIZATION postgres;
                    CREATE SCHEMA etaye_demo AUTHORIZATION etaye_owner;
                    """);
        } catch (SQLException ex) {
            throw new ExceptionInInitializerError(ex);
        }
        flyway(URL, false).migrate();
    }

    private DemoDatabase() {}

    public static void configureDemo(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> RUNTIME_USER);
        registry.add("spring.datasource.password", () -> RUNTIME_PASSWORD);
        registry.add("etaye.identity.datasource.password", () -> IDENTITY_PASSWORD);
    }

    public static String ordinaryUrl() {
        return POSTGRES_CONTAINER.getJdbcUrl();
    }

    public static Flyway flyway(String url, boolean outOfOrder) {
        return Flyway.configure().dataSource(url, OWNER_USER, OWNER_PASSWORD)
                .locations("classpath:db/migration", "classpath:db/demo").outOfOrder(outOfOrder).load();
    }

    public static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(URL, OWNER_USER, OWNER_PASSWORD);
    }

    /** Superuser connection for oracle queries that must see rows protected by forced row-level security. */
    public static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(URL, "postgres", POSTGRES_CONTAINER.getPassword());
    }
}
