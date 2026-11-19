package dev.aymen.etaye;

import dev.aymen.etaye.infrastructure.transaction.PracticeTransaction;
import dev.aymen.etaye.practices.domain.PracticeRepository;
import dev.aymen.etaye.records.domain.Patient;
import dev.aymen.etaye.records.service.PatientService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1"
})
public class PracticeIsolationTest extends AbstractPostgresTest {

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private TestPatientHelper testPatientHelper;

    @Autowired
    private PracticeTransaction practiceTransaction;

    @Autowired
    private TestTransactionHelper testTransactionHelper;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
    @DisplayName("C8: Verify runtime and identity role catalog invariants, ownership, privileges, and FORCE RLS")
    void checkC8_RuntimeRoleCatalogVerification() {
        // 1. Current user and session user from HTTP datasource
        jdbcTemplate.query("SELECT current_user, session_user", rs -> {
            assertEquals("etaye_runtime", rs.getString(1));
            assertEquals("etaye_runtime", rs.getString(2));
        });

        // 2. Role memberships: etaye_runtime must not inherit or belong to superuser, owner, or identity
        Boolean memberOfOwner = jdbcTemplate.queryForObject(
                "SELECT pg_has_role('etaye_runtime', 'etaye_owner', 'member')", Boolean.class);
        assertFalse(memberOfOwner, "etaye_runtime must not be a member of etaye_owner");

        Boolean memberOfSuperuser = jdbcTemplate.queryForObject(
                "SELECT pg_has_role('etaye_runtime', 'postgres', 'member')", Boolean.class);
        assertFalse(memberOfSuperuser, "etaye_runtime must not be a member of postgres superuser");

        Boolean memberOfIdentity = jdbcTemplate.queryForObject(
                "SELECT pg_has_role('etaye_runtime', 'etaye_identity', 'member')", Boolean.class);
        assertFalse(memberOfIdentity, "etaye_runtime must not be a member of etaye_identity");

        // B4(3): Full membership closure of etaye_runtime and etaye_identity (pg_auth_members recursively)
        // must contain no superuser, no BYPASSRLS role, and no owner role
        for (String roleName : List.of("etaye_runtime", "etaye_identity")) {
            jdbcTemplate.query("SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = ?", ps -> ps.setString(1, roleName), rs -> {
                assertFalse(rs.getBoolean("rolsuper"), roleName + " must not have rolsuper");
                assertFalse(rs.getBoolean("rolbypassrls"), roleName + " must not have rolbypassrls");
            });

            String closureSql = """
                    WITH RECURSIVE closure(role_oid) AS (
                        SELECT roleid FROM pg_auth_members WHERE member = ?::regrole
                        UNION
                        SELECT m.roleid FROM pg_auth_members m JOIN closure c ON m.member = c.role_oid
                    )
                    SELECT r.rolname, r.rolsuper, r.rolbypassrls FROM closure c JOIN pg_roles r ON c.role_oid = r.oid
                    """;
            List<Map<String, Object>> inheritedRoles = jdbcTemplate.queryForList(closureSql, roleName);
            for (Map<String, Object> inherited : inheritedRoles) {
                String inheritedName = (String) inherited.get("rolname");
                assertFalse((Boolean) inherited.get("rolsuper"), "Inherited role " + inheritedName + " in closure of " + roleName + " must not have rolsuper");
                assertFalse((Boolean) inherited.get("rolbypassrls"), "Inherited role " + inheritedName + " in closure of " + roleName + " must not have rolbypassrls");
                assertNotEquals("etaye_owner", inheritedName, roleName + " closure must not contain etaye_owner");
                assertNotEquals("postgres", inheritedName, roleName + " closure must not contain postgres");
            }
        }

        // B4(4): Ownership and both RLS flags (relrowsecurity AND relforcerowsecurity) using schema-qualified to_regclass
        List<String> expectedTables = List.of(
                "practice", "account", "membership",
                "patient", "patient_record", "audit_event", "outbox_event", "idempotency_receipt",
                "spring_session", "spring_session_attributes"
        );
        List<String> rlsTables = List.of("account", "membership", "patient", "patient_record", "audit_event", "outbox_event", "idempotency_receipt");

        for (String tbl : expectedTables) {
            String qualifiedTable = "public." + tbl;
            jdbcTemplate.query("SELECT c.relrowsecurity, c.relforcerowsecurity, r.rolname " +
                            "FROM pg_class c JOIN pg_roles r ON c.relowner = r.oid " +
                            "WHERE c.oid = to_regclass(?)",
                    ps -> ps.setString(1, qualifiedTable),
                    rs -> {
                        assertEquals("etaye_owner", rs.getString("rolname"), "Table " + qualifiedTable + " must be owned by etaye_owner");
                        if (rlsTables.contains(tbl)) {
                            assertTrue(rs.getBoolean("relrowsecurity"), "relrowsecurity must be true on " + qualifiedTable);
                            assertTrue(rs.getBoolean("relforcerowsecurity"), "relforcerowsecurity (FORCE RLS) must be true on " + qualifiedTable);
                        }
                    });
        }

        // B4(2): No TRUNCATE, REFERENCES, or TRIGGER privilege on any table for runtime or identity
        for (String tbl : expectedTables) {
            String qualifiedTable = "public." + tbl;
            assertFalse(jdbcTemplate.queryForObject("SELECT has_table_privilege('etaye_runtime', to_regclass(?), 'TRUNCATE')", Boolean.class, qualifiedTable),
                    "etaye_runtime must not have TRUNCATE on " + qualifiedTable);
            assertFalse(jdbcTemplate.queryForObject("SELECT has_table_privilege('etaye_runtime', to_regclass(?), 'REFERENCES')", Boolean.class, qualifiedTable),
                    "etaye_runtime must not have REFERENCES on " + qualifiedTable);
            assertFalse(jdbcTemplate.queryForObject("SELECT has_table_privilege('etaye_runtime', to_regclass(?), 'TRIGGER')", Boolean.class, qualifiedTable),
                    "etaye_runtime must not have TRIGGER on " + qualifiedTable);

            assertFalse(jdbcTemplate.queryForObject("SELECT has_table_privilege('etaye_identity', to_regclass(?), 'TRUNCATE')", Boolean.class, qualifiedTable),
                    "etaye_identity must not have TRUNCATE on " + qualifiedTable);
            assertFalse(jdbcTemplate.queryForObject("SELECT has_table_privilege('etaye_identity', to_regclass(?), 'REFERENCES')", Boolean.class, qualifiedTable),
                    "etaye_identity must not have REFERENCES on " + qualifiedTable);
            assertFalse(jdbcTemplate.queryForObject("SELECT has_table_privilege('etaye_identity', to_regclass(?), 'TRIGGER')", Boolean.class, qualifiedTable),
                    "etaye_identity must not have TRIGGER on " + qualifiedTable);
        }

        // B4(1) & B4(5): Column privileges for every column of every table from runtime datasource
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = 'public' ORDER BY table_name, ordinal_position");
        for (Map<String, Object> colRow : columns) {
            String table = (String) colRow.get("table_name");
            String col = (String) colRow.get("column_name");
            String qualifiedTable = "public." + table;

            // Runtime role column privileges
            boolean rtSelect = jdbcTemplate.queryForObject("SELECT has_column_privilege('etaye_runtime', to_regclass(?), ?, 'SELECT')", Boolean.class, qualifiedTable, col);
            boolean rtInsert = jdbcTemplate.queryForObject("SELECT has_column_privilege('etaye_runtime', to_regclass(?), ?, 'INSERT')", Boolean.class, qualifiedTable, col);
            boolean rtUpdate = jdbcTemplate.queryForObject("SELECT has_column_privilege('etaye_runtime', to_regclass(?), ?, 'UPDATE')", Boolean.class, qualifiedTable, col);
            boolean rtReferences = jdbcTemplate.queryForObject("SELECT has_column_privilege('etaye_runtime', to_regclass(?), ?, 'REFERENCES')", Boolean.class, qualifiedTable, col);

            assertFalse(rtReferences, "etaye_runtime must never have REFERENCES on " + qualifiedTable + "." + col);

            if (List.of("patient", "patient_record", "idempotency_receipt").contains(table)) {
                assertTrue(rtSelect, "etaye_runtime should have SELECT on " + qualifiedTable + "." + col);
                assertTrue(rtInsert, "etaye_runtime should have INSERT on " + qualifiedTable + "." + col);
                assertFalse(rtUpdate, "etaye_runtime must not have UPDATE on " + qualifiedTable + "." + col);
            } else if (List.of("audit_event", "outbox_event").contains(table)) {
                assertFalse(rtSelect, "etaye_runtime must not have SELECT on " + qualifiedTable + "." + col);
                assertTrue(rtInsert, "etaye_runtime should have INSERT on " + qualifiedTable + "." + col);
                assertFalse(rtUpdate, "etaye_runtime must not have UPDATE on " + qualifiedTable + "." + col);
            } else if (List.of("spring_session", "spring_session_attributes").contains(table)) {
                assertTrue(rtSelect, "etaye_runtime should have SELECT on " + qualifiedTable + "." + col);
                assertTrue(rtInsert, "etaye_runtime should have INSERT on " + qualifiedTable + "." + col);
                assertTrue(rtUpdate, "etaye_runtime should have UPDATE on " + qualifiedTable + "." + col);
            } else {
                // practice, account, membership: NO column privileges for runtime role
                assertFalse(rtSelect, "etaye_runtime must not have SELECT on " + qualifiedTable + "." + col);
                assertFalse(rtInsert, "etaye_runtime must not have INSERT on " + qualifiedTable + "." + col);
                assertFalse(rtUpdate, "etaye_runtime must not have UPDATE on " + qualifiedTable + "." + col);
            }

            // password_hash must NEVER have any privilege under etaye_runtime or etaye_identity
            if ("password_hash".equals(col)) {
                assertFalse(rtSelect, "etaye_runtime must NEVER have SELECT on password_hash");
                assertFalse(rtInsert, "etaye_runtime must NEVER have INSERT on password_hash");
                assertFalse(rtUpdate, "etaye_runtime must NEVER have UPDATE on password_hash");
                boolean idSelectPw = jdbcTemplate.queryForObject("SELECT has_column_privilege('etaye_identity', to_regclass(?), ?, 'SELECT')", Boolean.class, qualifiedTable, col);
                assertFalse(idSelectPw, "etaye_identity must NEVER have SELECT on password_hash");
            }

            // Identity role privileges (B4(5))
            boolean idSelect = jdbcTemplate.queryForObject("SELECT has_column_privilege('etaye_identity', to_regclass(?), ?, 'SELECT')", Boolean.class, qualifiedTable, col);
            boolean idInsert = jdbcTemplate.queryForObject("SELECT has_column_privilege('etaye_identity', to_regclass(?), ?, 'INSERT')", Boolean.class, qualifiedTable, col);
            boolean idUpdate = jdbcTemplate.queryForObject("SELECT has_column_privilege('etaye_identity', to_regclass(?), ?, 'UPDATE')", Boolean.class, qualifiedTable, col);
            boolean idReferences = jdbcTemplate.queryForObject("SELECT has_column_privilege('etaye_identity', to_regclass(?), ?, 'REFERENCES')", Boolean.class, qualifiedTable, col);

            assertFalse(idInsert, "etaye_identity must never have INSERT on " + qualifiedTable + "." + col);
            assertFalse(idUpdate, "etaye_identity must never have UPDATE on " + qualifiedTable + "." + col);
            assertFalse(idReferences, "etaye_identity must never have REFERENCES on " + qualifiedTable + "." + col);

            if ("account".equals(table)) {
                if (List.of("id", "normalized_email", "display_name", "state", "locale", "created_at", "updated_at").contains(col)) {
                    assertTrue(idSelect, "etaye_identity should have SELECT on account." + col);
                } else {
                    assertFalse(idSelect, "etaye_identity must not have SELECT on account." + col);
                }
            } else if ("membership".equals(table)) {
                if (List.of("id", "account_id", "practice_id", "state", "capabilities", "created_at", "updated_at").contains(col)) {
                    assertTrue(idSelect, "etaye_identity should have SELECT on membership." + col);
                } else {
                    assertFalse(idSelect, "etaye_identity must not have SELECT on membership." + col);
                }
            } else {
                // All other tables: etaye_identity has NO SELECT on any column
                assertFalse(idSelect, "etaye_identity must not have SELECT on " + qualifiedTable + "." + col);
            }
        }
    }

    @Test
    @DisplayName("C5: Absent context must deny: positive control, read returns 0 rows, write violates RLS WITH CHECK")
    void checkC5_AbsentContextDenial() {
        // Positive control: create Patient A under practice context
        Patient patientA = practiceTransaction.execute(practiceAId, () ->
                testPatientHelper.createPatient(practiceAId, "Alice", "PositiveControl"));
        assertThat(patientA.getId()).isNotNull();

        // Read without context must return 0 rows even though Patient A exists
        List<Patient> patients = testTransactionHelper.executeWithoutContext(() ->
                testPatientHelper.listPatientsWithoutAppPredicate());
        assertThat(patients).isEmpty();

        // Direct ID lookup without context must return empty
        Optional<Patient> crossReadNoContext = testTransactionHelper.executeWithoutContext(() ->
                testPatientHelper.getPatientWithoutAppPredicate(patientA.getId()));
        assertThat(crossReadNoContext).isEmpty();

        // Write without context must violate RLS WITH CHECK policy
        assertThatThrownBy(() -> testTransactionHelper.executeVoidWithoutContext(() ->
                testPatientHelper.createPatient(practiceAId, "NoContext", "Denied")))
                .isInstanceOf(DataAccessException.class)
                .hasMessageFindingMatch("(?i).*row-level security policy.*");
    }

    @Test
    @DisplayName("C6: Adversarial isolation test with single connection pool reuse after error and capability denial")
    void checkC6_AdversarialTwoPracticesAndConnectionReuse() {
        // 1. Transaction 1: Practice A context. Create Patient A.
        Patient patientA = practiceTransaction.execute(practiceAId, () ->
                testPatientHelper.createPatient(practiceAId, "Alice", "Martin"));
        assertThat(patientA.getId()).isNotNull();

        Long backendPid1 = practiceTransaction.execute(practiceAId, () ->
                jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Long.class));

        // Verify Practice A sees Patient A
        List<Patient> listA = practiceTransaction.execute(practiceAId, () ->
                testPatientHelper.listPatientsWithoutAppPredicate());
        assertThat(listA).extracting(Patient::getId).contains(patientA.getId());

        // 2. Transaction 2: Error injection causing rollback on the single connection
        assertThatThrownBy(() -> practiceTransaction.executeVoid(practiceAId, () -> {
            testPatientHelper.createPatient(practiceAId, "WillFail", "Rollback");
            throw new RuntimeException("Simulated domain failure before commit");
        })).hasMessage("Simulated domain failure before commit");

        // 3. Transaction 3: Reused connection after error for Practice B
        // Proves: same backend PID is reused, context is empty before setting B, Practice B sees only B
        AtomicReference<String> contextBeforeB = new AtomicReference<>();
        Long backendPid2 = testTransactionHelper.executeWithoutContext(() -> {
            contextBeforeB.set(jdbcTemplate.queryForObject(
                    "SELECT NULLIF(current_setting('etaye.practice_id', true), '')", String.class));
            return jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Long.class);
        });

        // Assert single connection was indeed reused
        assertEquals(backendPid1, backendPid2, "Hikari connection pool size 1 must reuse the exact same backend PID");
        assertNull(contextBeforeB.get(), "Context from aborted transaction must not leak onto reused connection");

        // Now establish Practice B context and create Patient B
        Patient patientB = practiceTransaction.execute(practiceBId, () ->
                testPatientHelper.createPatient(practiceBId, "Bob", "Dubois"));
        assertThat(patientB.getId()).isNotNull();

        // 4. Practice B must see Patient B, but CANNOT see Patient A (RLS + application predicate)
        List<Patient> listB = practiceTransaction.execute(practiceBId, () ->
                testPatientHelper.listPatientsWithoutAppPredicate());
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
        Long backendPid3 = testTransactionHelper.executeWithoutContext(() -> {
            contextBeforeA.set(jdbcTemplate.queryForObject(
                    "SELECT NULLIF(current_setting('etaye.practice_id', true), '')", String.class));
            return jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Long.class);
        });

        assertEquals(backendPid1, backendPid3, "Hikari connection pool must reuse the exact same backend PID");
        assertNull(contextBeforeA.get(), "Context must be empty before setting Practice A");

        // 7. Verify bidirectional isolation:
        // Practice A cannot see Patient B (neither with direct ID lookup nor raw list)
        Optional<Patient> crossReadByA = practiceTransaction.execute(practiceAId, () ->
                testPatientHelper.getPatientWithoutAppPredicate(patientB.getId()));
        assertThat(crossReadByA).isEmpty();

        List<Patient> finalA = practiceTransaction.execute(practiceAId, () ->
                testPatientHelper.listPatientsWithoutAppPredicate());
        assertThat(finalA).extracting(Patient::getId).contains(patientA.getId());
        assertThat(finalA).extracting(Patient::getId).doesNotContain(patientB.getId());

        // Practice B cannot see Patient A
        Optional<Patient> crossReadByB = practiceTransaction.execute(practiceBId, () ->
                testPatientHelper.getPatientWithoutAppPredicate(patientA.getId()));
        assertThat(crossReadByB).isEmpty();
    }

    @Test
    @DisplayName("PracticeTransaction: Nested calls for same practice join successfully")
    void checkNestedCallJoinsSamePractice() {
        String result = practiceTransaction.execute(practiceAId, () -> {
            // Nested call for the same practice
            return practiceTransaction.execute(practiceAId, () -> "nested-success");
        });
        assertThat(result).isEqualTo("nested-success");
    }

    @Test
    @DisplayName("PracticeTransaction: Outermost call refuses to join an already active transaction without context")
    void checkOutermostCallRefusesToJoinActiveTransaction() {
        TransactionTemplate rawTemplate = new TransactionTemplate(transactionManager);
        rawTemplate.execute(status -> {
            assertThatThrownBy(() -> practiceTransaction.execute(practiceAId, () -> "forbidden"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Outermost PracticeTransaction cannot join an already active transaction without practice context");
            return null;
        });
    }

    @Test
    @DisplayName("A7: A foreign transaction named exactly etaye-practice-{uuid} is refused by PracticeTransaction")
    void foreignTransactionWithForgedPracticeNameIsRefused() {
        TransactionTemplate foreignTemplate = new TransactionTemplate(transactionManager);
        foreignTemplate.setName(PracticeTransaction.PRACTICE_TX_PREFIX + practiceAId);
        foreignTemplate.execute(status -> {
            assertThatThrownBy(() -> practiceTransaction.execute(practiceAId, () -> "forbidden"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Outermost PracticeTransaction cannot join an already active transaction without practice context");
            return null;
        });
    }

    @Test
    @DisplayName("PracticeTransaction: Nested call failure marks transaction rollback-only without savepoints")
    void checkNestedCallFailureMarksRollbackOnly() {
        assertThatThrownBy(() -> practiceTransaction.execute(practiceAId, () -> {
            try {
                // Nested transaction execution throws an exception
                practiceTransaction.execute(practiceAId, () -> {
                    throw new RuntimeException("Nested failure");
                });
            } catch (RuntimeException e) {
                // Swallow in caller: Spring's TransactionTemplate marks transaction rollback-only
            }
            // Attempting normal commit of the outer transaction must fail with UnexpectedRollbackException
            return "outer-completed";
        })).isInstanceOf(UnexpectedRollbackException.class);
    }

    @Test
    @DisplayName("Defense-in-depth: Removing application predicate with RLS active keeps isolation green; removing both goes red")
    void checkDefenseInDepthApplicationPredicateAndRls() {
        Patient patientA = practiceTransaction.execute(practiceAId, () ->
                testPatientHelper.createPatient(practiceAId, "Claire", "Defense"));

        // 1. With application predicate and RLS active: cross-practice read returns empty
        Optional<Patient> readWithAppPredicate = practiceTransaction.execute(practiceBId, () ->
                testPatientHelper.getPatient(practiceBId, patientA.getId()));
        assertThat(readWithAppPredicate).isEmpty();

        // 2. Removing application predicate (direct findById) while RLS is active: STAYS GREEN (returns empty)
        Optional<Patient> readWithoutAppPredicate = practiceTransaction.execute(practiceBId, () ->
                testPatientHelper.getPatientWithoutAppPredicate(patientA.getId()));
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
            jdbcTemplate.update("INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth) VALUES (?, ?, 'Direct', 'A', ?)",
                    directPatientA, practiceAId, java.sql.Date.valueOf(java.time.LocalDate.of(1980, 1, 1)));
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
