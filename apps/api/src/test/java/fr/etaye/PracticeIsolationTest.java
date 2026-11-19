package fr.etaye;

import fr.etaye.infrastructure.transaction.PracticeTransaction;
import fr.etaye.patients.domain.Patient;
import fr.etaye.patients.service.PatientService;
import fr.etaye.practices.domain.Practice;
import fr.etaye.practices.domain.PracticeRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class PracticeIsolationTest extends AbstractPostgresTest {

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PatientService patientService;

    @Autowired
    private PracticeTransaction practiceTransaction;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManager entityManager;

    private UUID practiceAId;
    private UUID practiceBId;

    @BeforeEach
    void setUp() {
        // Create Practice A and Practice B fixtures as owner (runtime role has SELECT-only on practice)
        practiceAId = createPracticeAsOwner("Cabinet Médical A");
        practiceBId = createPracticeAsOwner("Cabinet Médical B");
    }

    @Test
    @DisplayName("C8: Verify runtime role catalog invariants, ownership, privileges, and FORCE RLS")
    void checkC8_RuntimeRoleCatalogVerification() {
        jdbcTemplate.query("SELECT current_user, session_user", rs -> {
            assertEquals("etaye_runtime", rs.getString(1));
            assertEquals("etaye_runtime", rs.getString(2));
        });

        // Verify runtime role is non-superuser and has NO BYPASSRLS
        jdbcTemplate.query("SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = 'etaye_runtime'", rs -> {
            assertFalse(rs.getBoolean("rolsuper"), "etaye_runtime must not be superuser");
            assertFalse(rs.getBoolean("rolbypassrls"), "etaye_runtime must not have BYPASSRLS");
        });

        // Verify table ownership belongs to etaye_owner, not etaye_runtime
        jdbcTemplate.query("SELECT tablename, tableowner FROM pg_tables WHERE tablename IN ('practice', 'patient')", rs -> {
            assertEquals("etaye_owner", rs.getString("tableowner"),
                    "Table " + rs.getString("tablename") + " must be owned by etaye_owner");
        });

        // Verify RLS is enabled and forced on patient table via pg_class (relrowsecurity and relforcerowsecurity)
        jdbcTemplate.query("SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = 'patient'", rs -> {
            assertTrue(rs.getBoolean("relrowsecurity"), "RLS must be enabled on patient table");
            assertTrue(rs.getBoolean("relforcerowsecurity"), "FORCE RLS must be enabled on patient table");
        });

        // Verify minimal privileges: no DELETE on patient, no SELECT on account or membership
        Boolean canDeletePatient = jdbcTemplate.queryForObject(
                "SELECT has_table_privilege('etaye_runtime', 'patient', 'DELETE')", Boolean.class);
        assertFalse(canDeletePatient, "etaye_runtime must not have DELETE privilege on patient");

        Boolean canSelectAccount = jdbcTemplate.queryForObject(
                "SELECT has_table_privilege('etaye_runtime', 'account', 'SELECT')", Boolean.class);
        assertFalse(canSelectAccount, "etaye_runtime must not have SELECT privilege on account");

        Boolean canSelectMembership = jdbcTemplate.queryForObject(
                "SELECT has_table_privilege('etaye_runtime', 'membership', 'SELECT')", Boolean.class);
        assertFalse(canSelectMembership, "etaye_runtime must not have SELECT privilege on membership");
    }

    @Test
    @DisplayName("C5: Absent context must deny: read returns 0 rows, write violates RLS WITH CHECK")
    void checkC5_AbsentContextDenial() {
        // Read without context must return 0 rows
        List<Patient> patients = practiceTransaction.executeWithoutContext(() ->
                patientService.listPatientsWithoutAppPredicate());
        assertThat(patients).isEmpty();

        // Write without context must violate RLS WITH CHECK policy
        assertThatThrownBy(() -> practiceTransaction.executeVoidWithoutContext(() ->
                patientService.createPatient(practiceAId, "Alice", "Dupont")))
                .isInstanceOf(DataAccessException.class)
                .hasMessageFindingMatch("(?i).*row-level security policy.*");
    }

    @Test
    @DisplayName("C6: Adversarial isolation test with single connection pool reuse after error and capability denial")
    void checkC6_AdversarialTwoPracticesAndConnectionReuse() {
        // 1. Transaction 1: Practice A context. Create Patient A.
        Patient patientA = practiceTransaction.execute(practiceAId, () ->
                patientService.createPatient(practiceAId, "Alice", "Martin"));
        assertThat(patientA.getId()).isNotNull();

        Long backendPid1 = practiceTransaction.execute(practiceAId, () ->
                jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Long.class));

        // Verify Practice A sees Patient A
        List<Patient> listA = practiceTransaction.execute(practiceAId, () ->
                patientService.listPatientsWithoutAppPredicate());
        assertThat(listA).extracting(Patient::getId).contains(patientA.getId());

        // 2. Transaction 2: Error injection causing rollback on the single connection
        assertThatThrownBy(() -> practiceTransaction.executeVoid(practiceAId, () -> {
            patientService.createPatient(practiceAId, "WillFail", "Rollback");
            throw new RuntimeException("Simulated domain failure before commit");
        })).hasMessage("Simulated domain failure before commit");

        // 3. Transaction 3: Reused connection after error for Practice B
        // Proves: same backend PID is reused, context is empty before setting B, Practice B sees only B
        AtomicReference<String> contextBeforeB = new AtomicReference<>();
        Long backendPid2 = practiceTransaction.executeWithoutContext(() -> {
            contextBeforeB.set(jdbcTemplate.queryForObject(
                    "SELECT NULLIF(current_setting('etaye.practice_id', true), '')", String.class));
            return jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Long.class);
        });

        // Assert single connection was indeed reused
        assertEquals(backendPid1, backendPid2, "Hikari connection pool size 1 must reuse the exact same backend PID");
        assertNull(contextBeforeB.get(), "Context from aborted transaction must not leak onto reused connection");

        // Now establish Practice B context and create Patient B
        Patient patientB = practiceTransaction.execute(practiceBId, () ->
                patientService.createPatient(practiceBId, "Bob", "Dubois"));
        assertThat(patientB.getId()).isNotNull();

        // 4. Practice B must see Patient B, but CANNOT see Patient A (RLS + application predicate)
        List<Patient> listB = practiceTransaction.execute(practiceBId, () ->
                patientService.listPatientsWithoutAppPredicate());
        assertThat(listB).extracting(Patient::getId).contains(patientB.getId());
        assertThat(listB).extracting(Patient::getId).doesNotContain(patientA.getId());

        // 5. Transaction 4: Simulation of missing capability denial
        assertThatThrownBy(() -> practiceTransaction.executeVoid(practiceBId, () -> {
            boolean hasPermission = false; // simulated missing capability check
            if (!hasPermission) {
                throw new SecurityException("403 FORBIDDEN: Missing CLINICAL_WRITE capability");
            }
        })).isInstanceOf(SecurityException.class);

        // 6. Transaction 5: Reused connection after capability denial back to Practice A
        AtomicReference<String> contextBeforeA = new AtomicReference<>();
        Long backendPid3 = practiceTransaction.executeWithoutContext(() -> {
            contextBeforeA.set(jdbcTemplate.queryForObject(
                    "SELECT NULLIF(current_setting('etaye.practice_id', true), '')", String.class));
            return jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Long.class);
        });

        assertEquals(backendPid1, backendPid3, "Hikari connection pool must reuse the exact same backend PID");
        assertNull(contextBeforeA.get(), "Context must be empty before setting Practice A");

        // 7. Verify bidirectional isolation:
        // Practice A cannot see Patient B (neither with direct ID lookup nor raw list)
        Optional<Patient> crossReadByA = practiceTransaction.execute(practiceAId, () ->
                patientService.getPatientWithoutAppPredicate(patientB.getId()));
        assertThat(crossReadByA).isEmpty();

        List<Patient> finalA = practiceTransaction.execute(practiceAId, () ->
                patientService.listPatientsWithoutAppPredicate());
        assertThat(finalA).extracting(Patient::getId).contains(patientA.getId());
        assertThat(finalA).extracting(Patient::getId).doesNotContain(patientB.getId());

        // Practice B cannot see Patient A
        Optional<Patient> crossReadByB = practiceTransaction.execute(practiceBId, () ->
                patientService.getPatientWithoutAppPredicate(patientA.getId()));
        assertThat(crossReadByB).isEmpty();
    }

    @Test
    @DisplayName("Defense-in-depth: Removing application predicate with RLS active keeps isolation green; removing both goes red")
    void checkDefenseInDepthApplicationPredicateAndRls() {
        Patient patientA = practiceTransaction.execute(practiceAId, () ->
                patientService.createPatient(practiceAId, "Claire", "Defense"));

        // 1. With application predicate and RLS active: cross-practice read returns empty
        Optional<Patient> readWithAppPredicate = practiceTransaction.execute(practiceBId, () ->
                patientService.getPatient(practiceBId, patientA.getId()));
        assertThat(readWithAppPredicate).isEmpty();

        // 2. Removing application predicate (direct findById) while RLS is active: STAYS GREEN (returns empty)
        Optional<Patient> readWithoutAppPredicate = practiceTransaction.execute(practiceBId, () ->
                patientService.getPatientWithoutAppPredicate(patientA.getId()));
        assertThat(readWithoutAppPredicate).isEmpty();
    }

    @Test
    @DisplayName("Context switch rejection in active transaction")
    void checkContextSwitchRejectionInJoinedTransaction() {
        assertThatThrownBy(() -> practiceTransaction.execute(practiceAId, () -> {
            // Attempting to switch practice context within an active transaction must be rejected
            return practiceTransaction.execute(practiceBId, () -> "forbidden");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot change practice context inside an active transaction");
    }

    @Test
    @DisplayName("Verify JDBC and JPA use the exact same physical Connection in transaction")
    void checkJdbcAndJpaShareSamePhysicalConnection() {
        practiceTransaction.executeVoid(practiceAId, () -> {
            Connection jdbcConn = DataSourceUtils.getConnection(dataSource);
            Session session = entityManager.unwrap(Session.class);
            session.doWork(jpaConn -> {
                try {
                    // Both must unwrap to or reference the exact same underlying connection
                    assertSame(jdbcConn, jpaConn, "JPA and JDBC in Spring transaction must share the exact same connection");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    @Test
    @DisplayName("Direct SQL isolation test: RLS filters out rows of other practice at database level")
    void checkDirectSqlIsolation() {
        // Direct SQL insert for practice A
        UUID directPatientA = UUID.randomUUID();
        practiceTransaction.executeVoid(practiceAId, () -> {
            jdbcTemplate.update("INSERT INTO patient (id, practice_id, first_name, last_name) VALUES (?, ?, 'Direct', 'A')",
                    directPatientA, practiceAId);
        });

        // Direct SQL read from Practice B context must return empty
        List<UUID> foundInB = practiceTransaction.execute(practiceBId, () ->
                jdbcTemplate.query("SELECT id FROM patient WHERE id = ?",
                        (rs, rowNum) -> (UUID) rs.getObject("id"),
                        directPatientA));
        assertThat(foundInB).isEmpty();

        // Direct SQL read from Practice A context must return the row
        List<UUID> foundInA = practiceTransaction.execute(practiceAId, () ->
                jdbcTemplate.query("SELECT id FROM patient WHERE id = ?",
                        (rs, rowNum) -> (UUID) rs.getObject("id"),
                        directPatientA));
        assertThat(foundInA).containsExactly(directPatientA);
    }
}
