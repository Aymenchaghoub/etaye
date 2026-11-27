package dev.aymen.etaye.infrastructure.demo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoFixtureManagedTest {

    private static final String DEMO_DATABASE = "etaye_demo_managed";
    private static final String RESTORE_DATABASE = "etaye_demo_restored";
    private static final String ADMIN = "etaye_test_admin";
    private static final String BOOTSTRAP_ROOT = "bootstrap_root";
    private static final String OWNER = "etaye_owner";
    private static final String RUNTIME = "etaye_runtime";
    private static final String IDENTITY = "etaye_identity";
    private static final String PRACTICE_A = "PRA-A";
    private static final String PRACTICE_B = "PRA-B";
    private static final UUID OPERATOR = UUID.fromString("2f18200a-ecb4-9aaa-9c5b-90342bb450e8");

    private final String adminPassword = UUID.randomUUID().toString();
    private final String ownerPassword = UUID.randomUUID().toString();
    private final String runtimePassword = UUID.randomUUID().toString();
    private final String identityPassword = UUID.randomUUID().toString();

    @Test
    void provisionsUpgradesSeedsResetsAndRestoresUnderNonSuperuserRoles() throws Exception {
        try (var pg = new PostgreSQLContainer("postgres:18")
                .withDatabaseName("bootstrap")
                .withUsername(BOOTSTRAP_ROOT)
                .withPassword(UUID.randomUUID().toString())) {
            pg.start();
            installScripts(pg);
            createAdministrator(pg);
            createDatabase(pg, DEMO_DATABASE);
            var runtime = jdbc(pg, DEMO_DATABASE, RUNTIME, runtimePassword);
            var owner = jdbc(pg, DEMO_DATABASE, OWNER, ownerPassword);
            var admin = jdbc(pg, DEMO_DATABASE, ADMIN, adminPassword);

            assertThat(admin.queryForObject("""
                    SELECT NOT (rolsuper OR rolbypassrls OR rolcreatedb) AND rolcreaterole
                    FROM pg_roles WHERE rolname = current_user
                    """, Boolean.class)).isTrue();
            assertThat(admin.queryForObject("SELECT to_regrole('postgres') IS NULL", Boolean.class)).isTrue();
            assertNoOwnerAccess(admin);

            var managed = new DemoFixture(runtime, DEMO_DATABASE);
            refuses(managed);
            assertThat(seed(pg, DEMO_DATABASE).getExitCode()).isNotZero();
            mark(pg, DEMO_DATABASE);
            mark(pg, DEMO_DATABASE);
            assertNoOwnerAccess(admin);
            assertThat(seed(pg, DEMO_DATABASE).getExitCode()).isNotZero();
            refuses(managed);

            var versionFour = Flyway.configure()
                    .dataSource(databaseUrl(pg, DEMO_DATABASE), OWNER, ownerPassword)
                    .locations("classpath:db/migration").target("4").load();
            assertThat(versionFour.migrate().migrationsExecuted).isEqualTo(4);
            versionFour.validate();
            assertThat(seed(pg, DEMO_DATABASE).getExitCode()).isNotZero();
            var throughMeasurements = Flyway.configure()
                    .dataSource(databaseUrl(pg, DEMO_DATABASE), OWNER, ownerPassword)
                    .locations("classpath:db/migration").target("11").load();
            assertThat(throughMeasurements.migrate().migrationsExecuted).isEqualTo(4);
            var flyway = schema(pg, DEMO_DATABASE);
            throughMeasurements.validate();

            var practiceId = UUID.randomUUID();
            owner.update("INSERT INTO practice (id, display_name) VALUES (?, ?)", practiceId, "Synthetic guard test");
            assertThat(seed(pg, DEMO_DATABASE).getStderr()).contains("database containing application data");
            assertThat(owner.queryForObject("SELECT id FROM practice", UUID.class)).isEqualTo(practiceId);
            owner.update("DELETE FROM practice WHERE id = ?", practiceId);

            // An audit row can exist without a practice row and be invisible under FORCE RLS.
            owner.execute("""
                    BEGIN;
                    SELECT set_config('etaye.practice_id', 'f09951ea-9cfc-4c3e-8191-d044a33d7ff4', true);
                    INSERT INTO audit_event (practice_id, event_type, resource_type, outcome, correlation_id)
                    VALUES ('f09951ea-9cfc-4c3e-8191-d044a33d7ff4', 'TEST', 'TEST', 'TEST', 'seed-refusal');
                    COMMIT;
                    """);
            assertThat(owner.queryForObject("SELECT count(*) FROM audit_event", Integer.class)).isZero();
            assertThat(seed(pg, DEMO_DATABASE).getStderr()).contains("initialized RLS heap: audit_event");
            owner.execute("TRUNCATE audit_event");

            owner.execute("ALTER TABLE measurement ADD CONSTRAINT test_seed_rollback CHECK (false)");
            assertThat(seed(pg, DEMO_DATABASE).getStderr()).contains("test_seed_rollback");
            assertThat(owner.queryForObject("SELECT count(*) FROM practice", Integer.class)).isZero();
            assertThat(owner.queryForObject("SELECT to_regclass('etaye_demo.fixture') IS NULL", Boolean.class)).isTrue();
            owner.execute("ALTER TABLE measurement DROP CONSTRAINT test_seed_rollback");

            assertSuccess(seed(pg, DEMO_DATABASE));
            assertSuccess(seed(pg, DEMO_DATABASE));
            assertThat(throughMeasurements.info().applied()).hasSize(8);
            throughMeasurements.validate();
            managed.verifyDatabase();
            assertThat(managed.version()).isEqualTo(new DemoFixture.Version("etaye-design-v1", 1L));
            refuses(new DemoFixture(runtime, ""));
            refuses(new DemoFixture(runtime, RESTORE_DATABASE));
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            flyway.validate();
            assertSuccess(seed(pg, DEMO_DATABASE));
            assertSuccess(seed(pg, DEMO_DATABASE));
            assertThat(managed.version().generation()).isEqualTo(1L);
            assertIsolation(pg, DEMO_DATABASE);

            reset(runtime, managed);
            assertThat(managed.version()).isEqualTo(new DemoFixture.Version("etaye-design-v1", 2L));
            assertSuccess(seed(pg, DEMO_DATABASE));
            assertThat(managed.version().generation()).isEqualTo(2L);

            restore(pg);
            schema(pg, RESTORE_DATABASE).validate();
            var restored = new DemoFixture(jdbc(pg, RESTORE_DATABASE, RUNTIME, runtimePassword), RESTORE_DATABASE);
            restored.verifyDatabase();
            assertThat(restored.version()).isEqualTo(managed.version());
            assertIsolation(pg, RESTORE_DATABASE);
            jdbc(pg, RESTORE_DATABASE, OWNER, ownerPassword)
                    .execute("DROP FUNCTION etaye_demo.reset_fixture(uuid, text, text, text, text)");
            refuses(restored);

            var freshDatabase = "etaye_demo_fresh";
            createDatabase(pg, freshDatabase);
            mark(pg, freshDatabase);
            var fresh = schema(pg, freshDatabase);
            assertThat(fresh.migrate().migrationsExecuted).isEqualTo(9);
            fresh.validate();
            assertSuccess(seed(pg, freshDatabase));
            jdbc(pg, freshDatabase, OWNER, ownerPassword).execute("DELETE FROM etaye_demo.fixture");
            refuses(new DemoFixture(jdbc(pg, freshDatabase, RUNTIME, runtimePassword), freshDatabase));

            var ordinaryDatabase = "etaye_ordinary";
            createDatabase(pg, ordinaryDatabase);
            schema(pg, ordinaryDatabase).migrate();
            assertThat(seed(pg, ordinaryDatabase).getExitCode()).isNotZero();
            refuses(new DemoFixture(jdbc(pg, ordinaryDatabase, RUNTIME, runtimePassword), ordinaryDatabase));
        }
    }

    private void createAdministrator(PostgreSQLContainer pg) {
        var root = jdbc(pg, "bootstrap", BOOTSTRAP_ROOT, pg.getPassword());
        root.execute("CREATE ROLE " + ADMIN + " WITH LOGIN NOSUPERUSER NOCREATEDB CREATEROLE NOBYPASSRLS");
        // This is a generated, ephemeral UUID, formatted by PostgreSQL before execution.
        var alterRole = root.queryForObject("SELECT format('ALTER ROLE %I PASSWORD %L', ?, ?)",
                String.class, ADMIN, adminPassword);
        root.execute(alterRole);
    }

    private void createDatabase(PostgreSQLContainer pg, String db) throws Exception {
        assertSuccess(pg.execInContainer("createdb", "-U", BOOTSTRAP_ROOT, "-O", ADMIN, db));
        provision(pg, db);
    }

    private void provision(PostgreSQLContainer pg, String db) throws Exception {
        assertSuccess(pg.execInContainer("env", "PGHOST=127.0.0.1", "POSTGRES_DB=" + db,
                "POSTGRES_ADMIN_USER=" + ADMIN, "POSTGRES_ADMIN_PASSWORD=" + adminPassword,
                "ETAYE_OWNER_PASSWORD=" + ownerPassword, "ETAYE_RUNTIME_PASSWORD=" + runtimePassword,
                "ETAYE_IDENTITY_PASSWORD=" + identityPassword, "bash", "/managed/infra/local/init-roles.sh"));
    }

    private void mark(PostgreSQLContainer pg, String db) throws Exception {
        assertSuccess(pg.execInContainer("env", "PGHOST=127.0.0.1", "POSTGRES_DB=" + db,
                "POSTGRES_ADMIN_USER=" + ADMIN, "POSTGRES_ADMIN_PASSWORD=" + adminPassword,
                "ETAYE_MANAGED_DEMO_DB=" + db, "bash", "/managed/infra/demo/mark-demo-database.sh"));
    }

    private ExecResult seed(PostgreSQLContainer pg, String db)
            throws IOException, InterruptedException {
        return pg.execInContainer("env", "PGHOST=127.0.0.1", "POSTGRES_DB=" + db,
                "ETAYE_MANAGED_DEMO_DB=" + db, "ETAYE_OWNER_PASSWORD=" + ownerPassword,
                "bash", "/managed/infra/demo/bootstrap-managed-demo.sh");
    }

    private static void installScripts(PostgreSQLContainer pg) throws Exception {
        var repository = Path.of("../..").toAbsolutePath().normalize();
        for (var path : new String[]{"infra/local/init-roles.sh", "infra/demo/mark-demo-database.sh",
                "infra/demo/bootstrap-managed-demo.sh", "apps/api/src/main/resources/db/demo"}) {
            pg.copyFileToContainer(MountableFile.forHostPath(repository.resolve(path)), "/managed/" + path);
        }
    }

    private static void assertNoOwnerAccess(JdbcTemplate admin) {
        assertThat(admin.queryForObject("""
                SELECT pg_has_role(current_user, 'etaye_owner', 'SET')
                    OR pg_has_role(current_user, 'etaye_owner', 'USAGE')
                """, Boolean.class)).isFalse();
    }

    private static void refuses(DemoFixture fixture) {
        assertThatThrownBy(fixture::verifyDatabase).isInstanceOf(IllegalStateException.class)
                .hasMessage("The demo requires a provisioned synthetic database");
    }

    private void assertIsolation(PostgreSQLContainer pg, String db) throws Exception {
        var owner = jdbc(pg, db, OWNER, ownerPassword);
        assertThat(owner.queryForList("""
                SELECT c.relname::text FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind = 'r' AND (c.relrowsecurity OR c.relforcerowsecurity)
                """, String.class)).containsExactlyInAnyOrder(
                "account", "membership", "patient", "patient_record", "audit_event", "outbox_event",
                "idempotency_receipt", "portal_link", "invitation", "source_revision", "clinical_assertion", "measurement", "lab_result");
        assertThat(owner.queryForObject("""
                SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind = 'r' AND c.relrowsecurity <> c.relforcerowsecurity
                """, Integer.class)).isZero();
        var identity = jdbc(pg, db, IDENTITY, identityPassword);
        assertThat(identity.queryForObject("SELECT count(id) FROM account", Integer.class)).isZero();
        assertThatThrownBy(() -> identity.queryForObject("SELECT count(*) FROM patient", Integer.class))
                .isInstanceOf(DataAccessException.class)
                .rootCause().isInstanceOfSatisfying(SQLException.class, ex -> assertThat(ex.getSQLState()).isEqualTo("42501"));
        var runtime = jdbc(pg, db, RUNTIME, runtimePassword);
        assertThatThrownBy(() -> runtime.execute("SET ROLE etaye_owner"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().isInstanceOfSatisfying(SQLException.class, ex -> assertThat(ex.getSQLState()).isEqualTo("42501"));
        try (var connection = runtime.getDataSource().getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            for (String alias : new String[]{PRACTICE_A, PRACTICE_B}) {
                try (var before = statement.executeQuery("SELECT count(*) FROM patient")) {
                    assertThat(before.next()).isTrue();
                    assertThat(before.getInt(1)).isZero();
                }
                try (var scope = connection.prepareStatement(
                        "SELECT set_config('etaye.practice_id', md5('etaye-design-v1/' || ?)::uuid::text, true)")) {
                    scope.setString(1, alias);
                    scope.execute();
                }
                try (var scoped = statement.executeQuery("SELECT count(*) FROM patient")) {
                    assertThat(scoped.next()).isTrue();
                    assertThat(scoped.getInt(1)).isEqualTo(alias.equals(PRACTICE_A) ? 3 : 1);
                }
                try (var labs = statement.executeQuery("SELECT count(*) FROM lab_result")) {
                    assertThat(labs.next()).isTrue();
                    assertThat(labs.getInt(1)).isEqualTo(alias.equals(PRACTICE_A) ? 9 : 0);
                }
                connection.commit();
            }
        }
    }

    private static void reset(JdbcTemplate runtime, DemoFixture fixture) throws InterruptedException {
        var session = UUID.randomUUID().toString();
        var now = System.currentTimeMillis();
        runtime.update("""
                INSERT INTO spring_session
                    (primary_id, session_id, creation_time, last_access_time, max_inactive_interval, expiry_time, principal_name)
                VALUES (?, ?, ?, ?, 300, ?, ?)
                """, UUID.randomUUID().toString(), session, now, now, now + 300_000, OPERATOR.toString());
        assertThat(fixture.reset(OPERATOR, session, "test-reset", "Synthetic reset", "test-correlation").outcome())
                .isEqualTo(DemoFixture.Outcome.RESET);
        assertThat(runtime.queryForObject("SELECT count(*) FROM spring_session", Integer.class)).isZero();
    }

    private void restore(PostgreSQLContainer pg) throws Exception {
        // The local backup is taken under a privileged role. The destination restores
        // application-owned objects as etaye_owner, with a newly provisioned admin marker.
        assertSuccess(pg.execInContainer("pg_dump", "-U", BOOTSTRAP_ROOT, "-Fc", "-n", "public", "-n", "etaye_demo",
                "-f", "/tmp/etaye.dump", DEMO_DATABASE));
        createDatabase(pg, RESTORE_DATABASE);
        mark(pg, RESTORE_DATABASE);
        assertSuccess(pg.execInContainer("bash", "-euo", "pipefail", "-c", """
                pg_restore --list /tmp/etaye.dump | sed -E '/^[0-9]+; .* SCHEMA - (public|etaye_demo) /d' > /tmp/restore.list
                """));
        assertSuccess(pg.execInContainer("env", "PGPASSWORD=" + ownerPassword, "pg_restore", "-h", "127.0.0.1",
                "-U", OWNER, "-d", RESTORE_DATABASE, "--no-owner", "--single-transaction", "--exit-on-error",
                "--use-list=/tmp/restore.list", "/tmp/etaye.dump"));
    }

    private Flyway schema(PostgreSQLContainer pg, String db) {
        return Flyway.configure().dataSource(databaseUrl(pg, db), OWNER, ownerPassword)
                .locations("classpath:db/migration").load();
    }

    private static String databaseUrl(PostgreSQLContainer pg, String db) {
        return pg.getJdbcUrl().replace("/bootstrap", "/" + db);
    }

    private static JdbcTemplate jdbc(PostgreSQLContainer pg, String db, String username, String password) {
        return new JdbcTemplate(new DriverManagerDataSource(databaseUrl(pg, db), username, password));
    }

    private static void assertSuccess(ExecResult result) {
        assertThat(result.getExitCode()).as("%s%n%s", result.getStdout(), result.getStderr()).isZero();
    }
}
