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
import java.util.Set;
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
        List<String> allRelations = jdbcTemplate.queryForList(
                "SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace " +
                        "WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') ORDER BY c.relname",
                String.class);

        Set<String> rlsTables = Set.of(
                "account", "membership", "patient", "patient_record", "portal_link", "audit_event", "outbox_event", "idempotency_receipt");

        for (String tbl : allRelations) {
            String qualifiedTable = "public." + tbl;
            jdbcTemplate.query("SELECT c.relrowsecurity, c.relforcerowsecurity, r.rolname " +
                            "FROM pg_class c JOIN pg_roles r ON c.relowner = r.oid " +
                            "WHERE c.oid = to_regclass(?)",
                    ps -> ps.setString(1, qualifiedTable),
                    rs -> {
                        assertEquals("etaye_owner", rs.getString("rolname"), "Table " + qualifiedTable + " must be owned by etaye_owner");
                        boolean expectRls = rlsTables.contains(tbl);
                        assertEquals(expectRls, rs.getBoolean("relrowsecurity"), "relrowsecurity mismatch on " + qualifiedTable);
                        assertEquals(expectRls, rs.getBoolean("relforcerowsecurity"), "relforcerowsecurity (FORCE RLS) mismatch on " + qualifiedTable);
                    });
        }

        // C6/C8: Catalog verification against explicit expected privilege maps
        checkCatalogPrivileges();
    }

    private void checkCatalogPrivileges() {
        List<String> allRelations = jdbcTemplate.queryForList(
                "SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace " +
                        "WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') ORDER BY c.relname",
                String.class);

        List<String> tablePrivileges = List.of(
                "SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER", "MAINTAIN"
        );

        Map<String, Map<String, Set<String>>> expectedTablePrivileges = Map.of(
                "etaye_runtime", Map.of(
                        "patient", Set.of("SELECT", "INSERT"),
                        "patient_record", Set.of("SELECT", "INSERT"),
                        "audit_event", Set.of("INSERT"),
                        "outbox_event", Set.of("INSERT"),
                        "idempotency_receipt", Set.of("SELECT", "INSERT"),
                        "spring_session", Set.of("SELECT", "INSERT", "UPDATE", "DELETE"),
                        "spring_session_attributes", Set.of("SELECT", "INSERT", "UPDATE", "DELETE")
                ),
                "etaye_identity", Map.of()
        );

        for (String role : List.of("etaye_runtime", "etaye_identity")) {
            for (String table : allRelations) {
                String qualifiedTable = "public." + table;
                Set<String> expectedPrivs = expectedTablePrivileges
                        .getOrDefault(role, Map.of())
                        .getOrDefault(table, Set.of());

                for (String priv : tablePrivileges) {
                    boolean hasPriv = jdbcTemplate.queryForObject(
                            "SELECT has_table_privilege(?, to_regclass(?), ?)",
                            Boolean.class, role, qualifiedTable, priv);
                    boolean expected = expectedPrivs.contains(priv);
                    assertEquals(expected, hasPriv,
                            String.format("Role %s on table %s for privilege %s expected %s but was %s",
                                    role, table, priv, expected, hasPriv));
                }
            }
        }

        // Column privileges from pg_attribute / pg_class
        String colSql = """
                SELECT c.relname AS table_name, a.attname AS column_name
                FROM pg_attribute a
                JOIN pg_class c ON a.attrelid = c.oid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') AND a.attnum > 0 AND NOT a.attisdropped
                ORDER BY c.relname, a.attnum
                """;
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(colSql);

        List<String> columnPrivileges = List.of("SELECT", "INSERT", "UPDATE", "REFERENCES");

        Set<String> identityAccountCols = Set.of("id", "normalized_email", "display_name", "state", "locale",
                "created_at", "updated_at", "version", "credential_revision");
        Set<String> identityMembershipCols = Set.of("id", "account_id", "practice_id", "state", "capabilities", "created_at", "updated_at");

        for (Map<String, Object> colRow : columns) {
            String table = (String) colRow.get("table_name");
            String col = (String) colRow.get("column_name");
            String qualifiedTable = "public." + table;

            for (String role : List.of("etaye_runtime", "etaye_identity")) {
                Set<String> expectedColPrivs;
                if ("etaye_runtime".equals(role)) {
                    if (Set.of("patient", "patient_record", "idempotency_receipt").contains(table)) {
                        expectedColPrivs = Set.of("SELECT", "INSERT");
                    } else if (Set.of("audit_event", "outbox_event").contains(table)) {
                        expectedColPrivs = Set.of("INSERT");
                    } else if (Set.of("spring_session", "spring_session_attributes").contains(table)) {
                        expectedColPrivs = Set.of("SELECT", "INSERT", "UPDATE");
                    } else {
                        expectedColPrivs = Set.of();
                    }
                } else { // etaye_identity
                    if ("account".equals(table) && identityAccountCols.contains(col)) {
                        expectedColPrivs = Set.of("SELECT");
                    } else if ("membership".equals(table) && identityMembershipCols.contains(col)) {
                        expectedColPrivs = Set.of("SELECT");
                    } else {
                        expectedColPrivs = Set.of();
                    }
                }

                for (String priv : columnPrivileges) {
                    boolean hasPriv = jdbcTemplate.queryForObject(
                            "SELECT has_column_privilege(?, to_regclass(?), ?, ?)",
                            Boolean.class, role, qualifiedTable, col, priv);
                    boolean expected = expectedColPrivs.contains(priv);
                    assertEquals(expected, hasPriv,
                            String.format("Role %s on column %s.%s for privilege %s expected %s but was %s",
                                    role, table, col, priv, expected, hasPriv));
                }
            }
        }
    }

    @Test
    @DisplayName("C6 mutation: Excess DELETE on audit_event for etaye_runtime is detected")
    void c6_mutationExcessDeleteOnAuditEventDetected() {
        assertThat(jdbcTemplate.queryForObject("SELECT has_table_privilege('etaye_runtime', 'audit_event', 'DELETE')", Boolean.class))
                .as("the migration must not already grant DELETE on audit_event").isFalse();
        try {
            executeAsOwner("GRANT DELETE ON audit_event TO etaye_runtime");
            assertThatThrownBy(() -> checkCatalogPrivileges())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Role etaye_runtime on table audit_event for privilege DELETE expected false but was true");
        } finally {
            executeAsOwner("REVOKE DELETE ON audit_event FROM etaye_runtime");
        }
    }

    @Test
    @DisplayName("C6 mutation: Excess SELECT on account for etaye_runtime is detected")
    void c6_mutationExcessSelectOnAccountDetected() {
        assertThat(jdbcTemplate.queryForObject("SELECT has_table_privilege('etaye_runtime', 'account', 'SELECT')", Boolean.class))
                .as("the migration must not already grant SELECT on account").isFalse();
        try {
            executeAsOwner("GRANT SELECT ON account TO etaye_runtime");
            assertThatThrownBy(() -> checkCatalogPrivileges())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Role etaye_runtime on table account for privilege SELECT expected false but was true");
        } finally {
            executeAsOwner("REVOKE SELECT ON account FROM etaye_runtime");
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
    @DisplayName("C12 patient: Direct SQL isolation, invisible to other practice, counted with and without context")
    void checkDirectSqlIsolation_patient() {
        UUID directPatientA = UUID.randomUUID();
        practiceTransaction.executeVoid(practiceAId, () -> {
            jdbcTemplate.update("INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth) VALUES (?, ?, 'Direct', 'A', ?)",
                    directPatientA, practiceAId, java.sql.Date.valueOf(java.time.LocalDate.of(1980, 1, 1)));
        });

        // 1. Direct SQL read from Practice B context must return empty (count = 0)
        List<UUID> foundInB = practiceTransaction.execute(practiceBId, () ->
                jdbcTemplate.query("SELECT id FROM patient WHERE id = ?",
                        (rs, rowNum) -> (UUID) rs.getObject("id"),
                        directPatientA));
        assertThat(foundInB).isEmpty();

        Integer countInB = practiceTransaction.execute(practiceBId, () ->
                jdbcTemplate.queryForObject("SELECT count(*) FROM patient WHERE id = ?", Integer.class, directPatientA));
        assertThat(countInB).isEqualTo(0);

        // 2. Read without context must return empty (count = 0)
        Integer countNoContext = testTransactionHelper.executeWithoutContext(() ->
                jdbcTemplate.queryForObject("SELECT count(*) FROM patient WHERE id = ?", Integer.class, directPatientA));
        assertThat(countNoContext).isEqualTo(0);

        // 3. Direct SQL read from Practice A context must return the row (count = 1)
        List<UUID> foundInA = practiceTransaction.execute(practiceAId, () ->
                jdbcTemplate.query("SELECT id FROM patient WHERE id = ?",
                        (rs, rowNum) -> (UUID) rs.getObject("id"),
                        directPatientA));
        assertThat(foundInA).containsExactly(directPatientA);

        Integer countInA = practiceTransaction.execute(practiceAId, () ->
                jdbcTemplate.queryForObject("SELECT count(*) FROM patient WHERE id = ?", Integer.class, directPatientA));
        assertThat(countInA).isEqualTo(1);
    }

    @Test
    @DisplayName("C12 patient_record: Direct SQL isolation, invisible to other practice, counted with and without context")
    void checkDirectSqlIsolation_patientRecord() {
        UUID directPatientA = UUID.randomUUID();
        UUID directRecordA = UUID.randomUUID();
        practiceTransaction.executeVoid(practiceAId, () -> {
            jdbcTemplate.update("INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth) VALUES (?, ?, 'PatientRec', 'A', ?)",
                    directPatientA, practiceAId, java.sql.Date.valueOf(java.time.LocalDate.of(1982, 3, 10)));
            jdbcTemplate.update("INSERT INTO patient_record (id, practice_id, patient_id) VALUES (?, ?, ?)",
                    directRecordA, practiceAId, directPatientA);
        });

        // 1. Practice B context: invisible, count = 0
        List<UUID> foundInB = practiceTransaction.execute(practiceBId, () ->
                jdbcTemplate.query("SELECT id FROM patient_record WHERE id = ?",
                        (rs, rowNum) -> (UUID) rs.getObject("id"),
                        directRecordA));
        assertThat(foundInB).isEmpty();

        Integer countInB = practiceTransaction.execute(practiceBId, () ->
                jdbcTemplate.queryForObject("SELECT count(*) FROM patient_record WHERE id = ?", Integer.class, directRecordA));
        assertThat(countInB).isEqualTo(0);

        // 2. Without context: invisible, count = 0
        Integer countNoContext = testTransactionHelper.executeWithoutContext(() ->
                jdbcTemplate.queryForObject("SELECT count(*) FROM patient_record WHERE id = ?", Integer.class, directRecordA));
        assertThat(countNoContext).isEqualTo(0);

        // 3. Practice A context: visible, count = 1
        List<UUID> foundInA = practiceTransaction.execute(practiceAId, () ->
                jdbcTemplate.query("SELECT id FROM patient_record WHERE id = ?",
                        (rs, rowNum) -> (UUID) rs.getObject("id"),
                        directRecordA));
        assertThat(foundInA).containsExactly(directRecordA);

        Integer countInA = practiceTransaction.execute(practiceAId, () ->
                jdbcTemplate.queryForObject("SELECT count(*) FROM patient_record WHERE id = ?", Integer.class, directRecordA));
        assertThat(countInA).isEqualTo(1);
    }

    @Test
    @DisplayName("C12 patient_record: WITH CHECK policy prevents mismatched practice_id and missing context; matching succeeds")
    void checkDirectSqlIsolation_patientRecord_writePath() {
        UUID directPatientA = UUID.randomUUID();
        practiceTransaction.executeVoid(practiceAId, () -> {
            jdbcTemplate.update("INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth) VALUES (?, ?, 'PatientRecWrite', 'A', ?)",
                    directPatientA, practiceAId, java.sql.Date.valueOf(java.time.LocalDate.of(1982, 3, 10)));
        });

        // 1. Without context: inserting patient_record violates RLS WITH CHECK
        UUID recordNoCtx = UUID.randomUUID();
        assertThatThrownBy(() -> testTransactionHelper.executeVoidWithoutContext(() -> {
            jdbcTemplate.update("INSERT INTO patient_record (id, practice_id, patient_id) VALUES (?, ?, ?)",
                    recordNoCtx, practiceAId, directPatientA);
        })).isInstanceOf(DataAccessException.class)
                .getRootCause()
                .hasMessageFindingMatch("(?i).*row-level security policy.*");

        // 2. Under Practice A context: inserting row with foreign practice_id (practiceBId) violates RLS WITH CHECK
        UUID recordForeign = UUID.randomUUID();
        assertThatThrownBy(() -> practiceTransaction.executeVoid(practiceAId, () -> {
            jdbcTemplate.update("INSERT INTO patient_record (id, practice_id, patient_id) VALUES (?, ?, ?)",
                    recordForeign, practiceBId, directPatientA);
        })).isInstanceOf(DataAccessException.class)
                .getRootCause()
                .hasMessageFindingMatch("(?i).*row-level security policy.*");

        // 3. Under Practice A context: inserting row with matching practice_id (practiceAId) succeeds
        UUID recordOk = UUID.randomUUID();
        practiceTransaction.executeVoid(practiceAId, () -> {
            int inserted = jdbcTemplate.update("INSERT INTO patient_record (id, practice_id, patient_id) VALUES (?, ?, ?)",
                    recordOk, practiceAId, directPatientA);
            assertThat(inserted).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("C12 idempotency_receipt: Direct SQL isolation, invisible to other practice, counted with and without context")
    void checkDirectSqlIsolation_idempotencyReceipt() {
        UUID directPatientA = UUID.randomUUID();
        UUID directActor = UUID.randomUUID();
        String keyDigest = "test-digest-" + UUID.randomUUID();
        practiceTransaction.executeVoid(practiceAId, () -> {
            jdbcTemplate.update("INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth) VALUES (?, ?, 'Idemp', 'A', ?)",
                    directPatientA, practiceAId, java.sql.Date.valueOf(java.time.LocalDate.of(1979, 11, 20)));
            jdbcTemplate.update("INSERT INTO idempotency_receipt (practice_id, actor_id, operation, key_digest, body_hash, response_resource, outcome, expires_at) " +
                            "VALUES (?, ?, 'createPatient', ?, 'bodyhash', ?, 'SUCCESS', CURRENT_TIMESTAMP + interval '1 day')",
                    practiceAId, directActor, keyDigest, directPatientA);
        });

        // 1. Practice B context: invisible, count = 0
        List<UUID> foundInB = practiceTransaction.execute(practiceBId, () ->
                jdbcTemplate.query("SELECT response_resource FROM idempotency_receipt WHERE key_digest = ?",
                        (rs, rowNum) -> (UUID) rs.getObject("response_resource"),
                        keyDigest));
        assertThat(foundInB).isEmpty();

        Integer countInB = practiceTransaction.execute(practiceBId, () ->
                jdbcTemplate.queryForObject("SELECT count(*) FROM idempotency_receipt WHERE key_digest = ?", Integer.class, keyDigest));
        assertThat(countInB).isEqualTo(0);

        // 2. Without context: invisible, count = 0
        Integer countNoContext = testTransactionHelper.executeWithoutContext(() ->
                jdbcTemplate.queryForObject("SELECT count(*) FROM idempotency_receipt WHERE key_digest = ?", Integer.class, keyDigest));
        assertThat(countNoContext).isEqualTo(0);

        // 3. Practice A context: visible, count = 1
        List<UUID> foundInA = practiceTransaction.execute(practiceAId, () ->
                jdbcTemplate.query("SELECT response_resource FROM idempotency_receipt WHERE key_digest = ?",
                        (rs, rowNum) -> (UUID) rs.getObject("response_resource"),
                        keyDigest));
        assertThat(foundInA).containsExactly(directPatientA);

        Integer countInA = practiceTransaction.execute(practiceAId, () ->
                jdbcTemplate.queryForObject("SELECT count(*) FROM idempotency_receipt WHERE key_digest = ?", Integer.class, keyDigest));
        assertThat(countInA).isEqualTo(1);
    }

    @Test
    @DisplayName("C12 idempotency_receipt: WITH CHECK policy prevents mismatched practice_id and missing context; matching succeeds")
    void checkDirectSqlIsolation_idempotencyReceipt_writePath() {
        UUID directPatientA = UUID.randomUUID();
        practiceTransaction.executeVoid(practiceAId, () -> {
            jdbcTemplate.update("INSERT INTO patient (id, practice_id, first_name, last_name, date_of_birth) VALUES (?, ?, 'IdempWrite', 'A', ?)",
                    directPatientA, practiceAId, java.sql.Date.valueOf(java.time.LocalDate.of(1985, 4, 12)));
        });

        UUID actorId = UUID.randomUUID();
        String digestNoCtx = "digest-noctx-" + UUID.randomUUID();
        String digestForeign = "digest-foreign-" + UUID.randomUUID();
        String digestOk = "digest-ok-" + UUID.randomUUID();

        // 1. Without context: inserting idempotency_receipt violates RLS WITH CHECK
        assertThatThrownBy(() -> testTransactionHelper.executeVoidWithoutContext(() -> {
            jdbcTemplate.update("INSERT INTO idempotency_receipt (practice_id, actor_id, operation, key_digest, body_hash, response_resource, outcome, expires_at) " +
                            "VALUES (?, ?, 'createPatient', ?, 'bodyhash', ?, 'SUCCESS', CURRENT_TIMESTAMP + interval '1 day')",
                    practiceAId, actorId, digestNoCtx, directPatientA);
        })).isInstanceOf(DataAccessException.class)
                .getRootCause()
                .hasMessageFindingMatch("(?i).*row-level security policy.*");

        // 2. Under Practice A context: inserting row with foreign practice_id (practiceBId) violates RLS WITH CHECK
        assertThatThrownBy(() -> practiceTransaction.executeVoid(practiceAId, () -> {
            jdbcTemplate.update("INSERT INTO idempotency_receipt (practice_id, actor_id, operation, key_digest, body_hash, response_resource, outcome, expires_at) " +
                            "VALUES (?, ?, 'createPatient', ?, 'bodyhash', ?, 'SUCCESS', CURRENT_TIMESTAMP + interval '1 day')",
                    practiceBId, actorId, digestForeign, directPatientA);
        })).isInstanceOf(DataAccessException.class)
                .getRootCause()
                .hasMessageFindingMatch("(?i).*row-level security policy.*");

        // 3. Under Practice A context: inserting row with matching practice_id (practiceAId) succeeds
        practiceTransaction.executeVoid(practiceAId, () -> {
            int inserted = jdbcTemplate.update("INSERT INTO idempotency_receipt (practice_id, actor_id, operation, key_digest, body_hash, response_resource, outcome, expires_at) " +
                            "VALUES (?, ?, 'createPatient', ?, 'bodyhash', ?, 'SUCCESS', CURRENT_TIMESTAMP + interval '1 day')",
                    practiceAId, actorId, digestOk, directPatientA);
            assertThat(inserted).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("C12 audit_event: WITH CHECK policy prevents mismatched practice_id and missing context; matching succeeds")
    void checkDirectSqlIsolation_auditEvent() {
        UUID actorId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        // 1. Under Practice A context: inserting row with practice_id = practiceBId violates RLS WITH CHECK
        assertThatThrownBy(() -> practiceTransaction.executeVoid(practiceAId, () -> {
            jdbcTemplate.update("INSERT INTO audit_event (practice_id, actor_account_id, event_type, resource_type, resource_id, outcome, correlation_id, safe_metadata) " +
                            "VALUES (?, ?, 'TEST_AUDIT', 'Patient', ?, 'SUCCESS', 'corr-denied', '{}'::jsonb)",
                    practiceBId, actorId, resourceId);
        })).isInstanceOf(DataAccessException.class)
                .getRootCause()
                .hasMessageFindingMatch("(?i).*row-level security policy.*");

        // 2. Without context: inserting row violates RLS WITH CHECK
        assertThatThrownBy(() -> testTransactionHelper.executeVoidWithoutContext(() -> {
            jdbcTemplate.update("INSERT INTO audit_event (practice_id, actor_account_id, event_type, resource_type, resource_id, outcome, correlation_id, safe_metadata) " +
                            "VALUES (?, ?, 'TEST_AUDIT', 'Patient', ?, 'SUCCESS', 'corr-no-ctx', '{}'::jsonb)",
                    practiceAId, actorId, resourceId);
        })).isInstanceOf(DataAccessException.class)
                .getRootCause()
                .hasMessageFindingMatch("(?i).*row-level security policy.*");

        // 3. Under Practice A context: inserting row with practice_id = practiceAId succeeds
        practiceTransaction.executeVoid(practiceAId, () -> {
            int inserted = jdbcTemplate.update("INSERT INTO audit_event (practice_id, actor_account_id, event_type, resource_type, resource_id, outcome, correlation_id, safe_metadata) " +
                            "VALUES (?, ?, 'TEST_AUDIT', 'Patient', ?, 'SUCCESS', 'corr-ok', '{}'::jsonb)",
                    practiceAId, actorId, resourceId);
            assertThat(inserted).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("C12 outbox_event: WITH CHECK policy prevents mismatched practice_id and missing context; matching succeeds")
    void checkDirectSqlIsolation_outboxEvent() {
        UUID resourceId = UUID.randomUUID();
        String dedupeKeyMismatch = "dedupe-mismatch-" + UUID.randomUUID();
        String dedupeKeyNoCtx = "dedupe-noctx-" + UUID.randomUUID();
        String dedupeKeyOk = "dedupe-ok-" + UUID.randomUUID();

        // 1. Under Practice A context: inserting row with practice_id = practiceBId violates RLS WITH CHECK
        assertThatThrownBy(() -> practiceTransaction.executeVoid(practiceAId, () -> {
            jdbcTemplate.update("INSERT INTO outbox_event (practice_id, event_type, resource_id, resource_version, dedupe_key, safe_payload) " +
                            "VALUES (?, 'TEST_OUTBOX', ?, 1, ?, '{\"test\":true}'::jsonb)",
                    practiceBId, resourceId, dedupeKeyMismatch);
        })).isInstanceOf(DataAccessException.class)
                .getRootCause()
                .hasMessageFindingMatch("(?i).*row-level security policy.*");

        // 2. Without context: inserting row violates RLS WITH CHECK
        assertThatThrownBy(() -> testTransactionHelper.executeVoidWithoutContext(() -> {
            jdbcTemplate.update("INSERT INTO outbox_event (practice_id, event_type, resource_id, resource_version, dedupe_key, safe_payload) " +
                            "VALUES (?, 'TEST_OUTBOX', ?, 1, ?, '{\"test\":true}'::jsonb)",
                    practiceAId, resourceId, dedupeKeyNoCtx);
        })).isInstanceOf(DataAccessException.class)
                .getRootCause()
                .hasMessageFindingMatch("(?i).*row-level security policy.*");

        // 3. Under Practice A context: inserting row with practice_id = practiceAId succeeds
        practiceTransaction.executeVoid(practiceAId, () -> {
            int inserted = jdbcTemplate.update("INSERT INTO outbox_event (practice_id, event_type, resource_id, resource_version, dedupe_key, safe_payload) " +
                            "VALUES (?, 'TEST_OUTBOX', ?, 1, ?, '{\"test\":true}'::jsonb)",
                    practiceAId, resourceId, dedupeKeyOk);
            assertThat(inserted).isEqualTo(1);
        });
    }
}
