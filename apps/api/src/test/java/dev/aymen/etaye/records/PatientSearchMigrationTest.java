package dev.aymen.etaye.records;

import dev.aymen.etaye.AbstractPostgresTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Upgrades an isolated database from V7 to V8 with a synthetic patient in place: the order keys are
 * computed for existing rows, the normalization is the single definition, and the runtime role can
 * correct only the identity columns.
 */
public class PatientSearchMigrationTest extends AbstractPostgresTest {

    private static final String DATABASE = "etaye_upgrade_v8";

    private static String url() {
        return POSTGRES_CONTAINER.getJdbcUrl().replace("/etaye_test", "/" + DATABASE);
    }

    private static void admin(String database, String sql) throws SQLException {
        String adminUrl = POSTGRES_CONTAINER.getJdbcUrl().replace("/etaye_test", "/" + database);
        try (Connection c = DriverManager.getConnection(adminUrl, POSTGRES_CONTAINER.getUsername(), POSTGRES_CONTAINER.getPassword());
             Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    @Test
    void upgradesFromV7KeepingSeededPatients() throws Exception {
        admin("etaye_test", "CREATE DATABASE " + DATABASE + " OWNER " + OWNER_USER);
        admin(DATABASE, "GRANT CONNECT ON DATABASE " + DATABASE + " TO " + RUNTIME_USER + ", " + IDENTITY_USER
                + "; ALTER SCHEMA public OWNER TO " + OWNER_USER + "; GRANT USAGE ON SCHEMA public TO " + RUNTIME_USER + ", " + IDENTITY_USER);

        Flyway.configure().dataSource(url(), OWNER_USER, OWNER_PASSWORD).locations("classpath:db/migration").target("7").load().migrate();
        UUID practice = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(url(), OWNER_USER, OWNER_PASSWORD); Statement st = c.createStatement()) {
            st.execute("INSERT INTO practice (id, display_name, timezone) VALUES ('" + practice + "', 'Cabinet upgrade', 'Europe/Paris')");
            st.execute("BEGIN; SELECT set_config('etaye.practice_id', '" + practice + "', true); "
                    + "INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth) VALUES ('" + patient + "', '" + practice
                    + "', 'Éloïse', 'Léger-Müller', '1985-11-02'); COMMIT;");
        }

        var result = Flyway.configure().dataSource(url(), OWNER_USER, OWNER_PASSWORD).locations("classpath:db/migration").target("8").load().migrate();
        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("8");

        try (Connection c = DriverManager.getConnection(url(), OWNER_USER, OWNER_PASSWORD); Statement st = c.createStatement()) {
            st.execute("SELECT set_config('etaye.practice_id', '" + practice + "', false)");
            try (ResultSet rs = st.executeQuery("SELECT first_name, first_name_key, last_name_key FROM patient WHERE id = '" + patient + "'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("first_name")).isEqualTo("Éloïse");
                assertThat(rs.getString("first_name_key")).isEqualTo("eloise");
                assertThat(rs.getString("last_name_key")).isEqualTo("leger muller");
            }
            try (ResultSet rs = st.executeQuery("SELECT patient_search_key('Œuvre-d''Ætna  ß') AS k, patient_search_key('---') AS empty, "
                    + "patient_search_key(E'Le\\u0301ger') AS decomposed, patient_name_key('---') AS fallback")) {
                rs.next();
                assertThat(rs.getString("k")).isEqualTo("oeuvre d aetna ss");
                assertThat(rs.getString("empty")).isEmpty();
                assertThat(rs.getString("decomposed")).isEqualTo("leger");
                assertThat(rs.getString("fallback")).isEqualTo("---");
            }
        }

        // The runtime role corrects identity columns only: never the practice, the id or the order keys.
        try (Connection c = DriverManager.getConnection(url(), RUNTIME_USER, RUNTIME_PASSWORD); Statement st = c.createStatement()) {
            st.execute("SELECT set_config('etaye.practice_id', '" + practice + "', false)");
            assertThat(st.executeUpdate("UPDATE patient SET first_name = 'Éloïse-Anne', version = version + 1 WHERE id = '" + patient + "'")).isEqualTo(1);
            assertThatThrownBy(() -> st.execute("UPDATE patient SET practice_id = '" + UUID.randomUUID() + "' WHERE id = '" + patient + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> st.execute("UPDATE patient SET last_name_key = 'x' WHERE id = '" + patient + "'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> st.execute("UPDATE patient_record SET practice_id = '" + UUID.randomUUID() + "' WHERE patient_id = '" + patient + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
        }
    }
}
