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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upgrades isolated databases to the measurements of story 3.3: an ordinary database from V9 to V11 with a synthetic
 * record and source in place, and an existing demonstration database from V10 to V12, which must seed the measurements
 * and keep the reset able to truncate and restore every table, including the new one.
 */
public class MeasurementMigrationTest extends AbstractPostgresTest {

    private static String url(String database) {
        return POSTGRES_CONTAINER.getJdbcUrl().replace("/etaye_test", "/" + database);
    }

    private static void admin(String database, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(url(database), POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword());
             Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    @Test
    void upgradesFromV9KeepingTheEvidenceAndAddingTheTimeZoneToTheContexts() throws Exception {
        String database = "etaye_upgrade_v11";
        admin("etaye_test", "CREATE DATABASE " + database + " OWNER " + OWNER_USER);
        admin(database, "GRANT CONNECT ON DATABASE " + database + " TO " + RUNTIME_USER + ", " + IDENTITY_USER
                + "; ALTER SCHEMA public OWNER TO " + OWNER_USER + "; GRANT USAGE ON SCHEMA public TO " + RUNTIME_USER + ", " + IDENTITY_USER);
        Flyway.configure().dataSource(url(database), OWNER_USER, OWNER_PASSWORD).locations("classpath:db/migration").target("9").load().migrate();

        UUID practice = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID record = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        UUID membership = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(url(database), OWNER_USER, OWNER_PASSWORD); Statement st = c.createStatement()) {
            st.execute("INSERT INTO practice (id, display_name, timezone) VALUES ('" + practice + "', 'Cabinet upgrade', 'Europe/Paris')");
            st.execute("INSERT INTO account (id, normalized_email, display_name, password_hash, state) VALUES ('" + account
                    + "', 'upgrade@example.test', 'Dr Upgrade', 'x', 'ACTIVE')");
            st.execute("BEGIN; SELECT set_config('etaye.practice_id', '" + practice + "', true); "
                    + "INSERT INTO membership (id, account_id, practice_id, capabilities) VALUES ('" + membership + "', '" + account + "', '"
                    + practice + "', ARRAY['CLINICAL_READ']); "
                    + "INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth) VALUES ('" + patient + "', '" + practice
                    + "', 'Éloïse', 'Léger', '1985-11-02'); "
                    + "INSERT INTO patient_record (id, practice_id, patient_id) VALUES ('" + record + "', '" + practice + "', '" + patient + "'); "
                    + "INSERT INTO source_revision (id, practice_id, record_id, lineage_id, revision, reference, kind, label, source_type, event_precision, recorded_at, author_label) "
                    + "VALUES ('" + source + "', '" + practice + "', '" + record + "', gen_random_uuid(), 1, 'SRC-U-1', 'PRACTITIONER_MEASUREMENT', 'Poids', 'PRACTITIONER_OBSERVED', 'UNKNOWN', now(), 'Dr Test'); COMMIT;");
        }

        var result = Flyway.configure().dataSource(url(database), OWNER_USER, OWNER_PASSWORD).locations("classpath:db/migration").target("11").load().migrate();
        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("11");

        try (Connection c = DriverManager.getConnection(url(database), OWNER_USER, OWNER_PASSWORD); Statement st = c.createStatement()) {
            // The evidence of V9 is untouched and the new table is row-level secured, forced and empty.
            st.execute("SELECT set_config('etaye.practice_id', '" + practice + "', false)");
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM source_revision WHERE id = '" + source + "'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            try (ResultSet rs = st.executeQuery("SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = 'measurement'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean(1)).isTrue();
                assertThat(rs.getBoolean(2)).isTrue();
            }
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM measurement")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
            // The replaced context function keeps its V4 columns and adds the practice zone, for a membership only. Like the
            // application, the caller names the account whose grants it asks for.
            st.execute("SELECT set_config('etaye.account_id', '" + account + "', false)");
            try (ResultSet rs = st.executeQuery("SELECT grant_id, grant_type, practice_name, display_role, practice_timezone FROM public.current_access_contexts('" + account + "')")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("grant_type")).isEqualTo("MEMBERSHIP");
                assertThat(rs.getString("practice_name")).isEqualTo("Cabinet upgrade");
                assertThat(rs.getString("display_role")).isEqualTo("CLINICAL");
                assertThat(rs.getString("practice_timezone")).isEqualTo("Europe/Paris");
                assertThat(rs.next()).isFalse();
            }
        }
        // The identity role keeps the right to execute the function and nothing else on the new table.
        try (Connection c = DriverManager.getConnection(url(database), IDENTITY_USER, IDENTITY_PASSWORD); Statement st = c.createStatement()) {
            st.execute("SELECT set_config('etaye.account_id', '" + account + "', false)");
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM public.current_access_contexts('" + account + "')")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void upgradesAnExistingDemonstrationDatabaseAndSeedsItsMeasurements() throws Exception {
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
            // The package as it was before story 3.3: every migration up to V10, the evidence seed included.
            Flyway.configure().dataSource(container.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD)
                    .locations("classpath:db/migration", "classpath:db/demo").target("10").load().migrate();
            try (Connection c = DriverManager.getConnection(container.getJdbcUrl(), "postgres", container.getPassword());
                 Statement st = c.createStatement()) {
                assertThat(count(st, "source_revision")).isEqualTo(10);
            }

            var result = Flyway.configure().dataSource(container.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD)
                    .locations("classpath:db/migration", "classpath:db/demo").target("12").load().migrate();
            assertThat(result.success).isTrue();
            assertThat(result.targetSchemaVersion).isEqualTo("12");

            try (Connection c = DriverManager.getConnection(container.getJdbcUrl(), "postgres", container.getPassword());
                 Statement st = c.createStatement()) {
                assertThat(count(st, "measurement")).isEqualTo(6);
                assertThat(seededMeasurements(st)).containsExactly(
                        "MEAS-A-001:DIASTOLIC_BLOOD_PRESSURE:80:mmHg:1:SRC-A-008",
                        "MEAS-A-001:SYSTOLIC_BLOOD_PRESSURE:128:mmHg:1:SRC-A-008",
                        "MEAS-A-002:DIASTOLIC_BLOOD_PRESSURE:82:mmHg:1:SRC-A-004",
                        "MEAS-A-002:SYSTOLIC_BLOOD_PRESSURE:134:mmHg:1:SRC-A-004",
                        "MEAS-A-003:WEIGHT:86.4:kg:1:SRC-A-007-v1",
                        "MEAS-A-003:WEIGHT:68.4:kg:2:SRC-A-007-v2");
                // The correction reason is the one stored on the source revision: a single wording.
                try (ResultSet rs = st.executeQuery("""
                        SELECT count(*) FROM public.measurement m JOIN public.source_revision s ON s.id = m.source_revision_id
                        WHERE m.revision = 2 AND m.correction_reason = s.correction_reason""")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
                // The two components of a reading share one group; the weight has none.
                try (ResultSet rs = st.executeQuery("SELECT count(DISTINCT group_id), count(group_id) FROM public.measurement")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(2);
                    assertThat(rs.getInt(2)).isEqualTo(4);
                }
                // A reload truncates every table, including the new one that holds keys to the records and sources.
                st.execute("SELECT etaye_demo.restore_fixture()");
                assertThat(count(st, "measurement")).isEqualTo(6);
                assertThat(count(st, "source_revision")).isEqualTo(10);
                // The repair mode of V6 leaves the measurements as they are: it neither duplicates nor removes them.
                st.execute("SELECT etaye_demo.restore_fixture(false)");
                assertThat(count(st, "measurement")).isEqualTo(6);
            }
        }
    }

    private static List<String> seededMeasurements(Statement st) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (ResultSet rs = st.executeQuery("""
                SELECT s.locator, m.concept, m.value_decimal::text, m.unit, m.revision, s.reference
                FROM public.measurement m JOIN public.source_revision s ON s.id = m.source_revision_id
                ORDER BY s.locator, m.concept, m.revision""")) {
            while (rs.next()) {
                rows.add(String.join(":", rs.getString(1).replaceAll(":version-\\d$", ""), rs.getString(2), rs.getString(3),
                        rs.getString(4), String.valueOf(rs.getInt(5)), rs.getString(6)));
            }
        }
        return rows;
    }

    private static int count(Statement st, String table) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT count(*) FROM public." + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
