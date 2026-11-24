package dev.aymen.etaye.records;

import dev.aymen.etaye.AbstractPostgresTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upgrades isolated databases to the evidence tables of story 3.2: an ordinary database from V8 to V9 with a
 * synthetic patient in place, and an existing demonstration database from V8 to V10, which must seed the evidence
 * and keep the reset able to truncate and restore every table.
 */
public class RecordEvidenceMigrationTest extends AbstractPostgresTest {

    private static String url(String database) {
        return POSTGRES_CONTAINER.getJdbcUrl().replace("/etaye_test", "/" + database);
    }

    private static void admin(String database, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(url(database), POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword());
             Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private static void provision(String database) throws SQLException {
        admin("etaye_test", "CREATE DATABASE " + database + " OWNER " + OWNER_USER);
        admin(database, "GRANT CONNECT ON DATABASE " + database + " TO " + RUNTIME_USER + ", " + IDENTITY_USER
                + "; ALTER SCHEMA public OWNER TO " + OWNER_USER + "; GRANT USAGE ON SCHEMA public TO " + RUNTIME_USER + ", " + IDENTITY_USER);
    }

    @Test
    void upgradesFromV8KeepingSeededPatientsAndForcingRowLevelSecurity() throws Exception {
        String database = "etaye_upgrade_v9";
        provision(database);
        Flyway.configure().dataSource(url(database), OWNER_USER, OWNER_PASSWORD).locations("classpath:db/migration").target("8").load().migrate();
        UUID practice = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID record = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(url(database), OWNER_USER, OWNER_PASSWORD); Statement st = c.createStatement()) {
            st.execute("INSERT INTO practice (id, display_name, timezone) VALUES ('" + practice + "', 'Cabinet upgrade', 'Europe/Paris')");
            st.execute("BEGIN; SELECT set_config('etaye.practice_id', '" + practice + "', true); "
                    + "INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth) VALUES ('" + patient + "', '" + practice
                    + "', 'Éloïse', 'Léger', '1985-11-02'); "
                    + "INSERT INTO patient_record (id, practice_id, patient_id) VALUES ('" + record + "', '" + practice + "', '" + patient + "'); COMMIT;");
        }

        var result = Flyway.configure().dataSource(url(database), OWNER_USER, OWNER_PASSWORD).locations("classpath:db/migration").load().migrate();
        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("9");

        try (Connection c = DriverManager.getConnection(url(database), OWNER_USER, OWNER_PASSWORD); Statement st = c.createStatement()) {
            st.execute("SELECT set_config('etaye.practice_id', '" + practice + "', false)");
            try (ResultSet rs = st.executeQuery("SELECT p.first_name, r.record_revision FROM patient p JOIN patient_record r ON r.patient_id = p.id WHERE p.id = '" + patient + "'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("Éloïse");
                assertThat(rs.getInt(2)).isEqualTo(1);
            }
            for (String table : new String[]{"source_revision", "clinical_assertion"}) {
                try (ResultSet rs = st.executeQuery("SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = '" + table + "'")) {
                    assertThat(rs.next()).as(table).isTrue();
                    assertThat(rs.getBoolean(1)).as(table + " row-level security").isTrue();
                    assertThat(rs.getBoolean(2)).as(table + " forced").isTrue();
                }
                try (ResultSet rs = st.executeQuery("SELECT count(*) FROM " + table)) {
                    rs.next();
                    assertThat(rs.getInt(1)).as(table + " starts empty").isZero();
                }
            }
            // The practice and the record that own a revision cannot be bypassed: a foreign record is a key violation.
            st.execute("INSERT INTO source_revision (practice_id, record_id, lineage_id, revision, reference, kind, label, source_type, event_precision, recorded_at, author_label) "
                    + "VALUES ('" + practice + "', '" + record + "', gen_random_uuid(), 1, 'SRC-U-1', 'CONSULTATION_NOTE', 'Note', 'PRACTITIONER_OBSERVED', 'UNKNOWN', now(), 'Dr Test')");
        }
    }

    @Test
    void upgradesAnExistingDemonstrationDatabaseAndSeedsItsEvidence() throws Exception {
        // The demonstration fixture refuses any database but "etaye_demo", which the shared test JVM already uses, so
        // the upgrade runs on its own container, provisioned like the package does.
        try (PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:18")
                .withDatabaseName("etaye_demo").withUsername("postgres").withPassword("upgrade-" + UUID.randomUUID())) {
            container.start();
            try (Connection c = DriverManager.getConnection(container.getJdbcUrl(), "postgres", container.getPassword());
                 Statement st = c.createStatement()) {
                st.execute("CREATE ROLE " + OWNER_USER + " LOGIN PASSWORD '" + OWNER_PASSWORD + "' NOSUPERUSER NOBYPASSRLS");
                st.execute("CREATE ROLE " + RUNTIME_USER + " LOGIN PASSWORD '" + RUNTIME_PASSWORD + "' NOSUPERUSER NOBYPASSRLS");
                st.execute("CREATE ROLE " + IDENTITY_USER + " LOGIN PASSWORD '" + IDENTITY_PASSWORD + "' NOSUPERUSER NOBYPASSRLS");
                st.execute("ALTER SCHEMA public OWNER TO " + OWNER_USER);
                st.execute("GRANT USAGE ON SCHEMA public TO " + RUNTIME_USER + ", " + IDENTITY_USER);
                st.execute("CREATE SCHEMA etaye_demo_bootstrap AUTHORIZATION postgres");
                st.execute("CREATE SCHEMA etaye_demo AUTHORIZATION " + OWNER_USER);
            }
            // The package as it was before story 3.2: every migration up to V8, the demo fixture included.
            Flyway.configure().dataSource(container.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD)
                    .locations("classpath:db/migration", "classpath:db/demo").target("8").load().migrate();
            try (Connection c = DriverManager.getConnection(container.getJdbcUrl(), "postgres", container.getPassword());
                 Statement st = c.createStatement()) {
                assertThat(count(st, "patient_record")).isEqualTo(4);
            }

            var result = Flyway.configure().dataSource(container.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD)
                    .locations("classpath:db/migration", "classpath:db/demo").load().migrate();
            assertThat(result.success).isTrue();
            assertThat(result.targetSchemaVersion).isEqualTo("10");

            try (Connection c = DriverManager.getConnection(container.getJdbcUrl(), "postgres", container.getPassword());
                 Statement st = c.createStatement()) {
                assertThat(count(st, "source_revision")).isEqualTo(10);
                assertThat(count(st, "clinical_assertion")).isEqualTo(4);
                // The seeded statements are the per-statement decomposition of the design aggregates.
                try (ResultSet rs = st.executeQuery("""
                        SELECT count(*) FROM public.clinical_assertion a JOIN public.source_revision s ON s.id = a.source_revision_id
                        WHERE a.category = 'ALLERGY' AND s.reference IN ('SRC-A-001', 'SRC-A-005')""")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(2);
                }
                // A reload truncates every table, including the new ones that hold keys to the records, then seeds again.
                st.execute("SELECT etaye_demo.restore_fixture()");
                assertThat(count(st, "source_revision")).isEqualTo(10);
                assertThat(count(st, "clinical_assertion")).isEqualTo(4);
                // The repair mode of V6 leaves the evidence as it is: it neither duplicates nor removes it.
                st.execute("SELECT etaye_demo.restore_fixture(false)");
                assertThat(count(st, "source_revision")).isEqualTo(10);
            }
        }
    }

    private static int count(Statement st, String table) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT count(*) FROM public." + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
