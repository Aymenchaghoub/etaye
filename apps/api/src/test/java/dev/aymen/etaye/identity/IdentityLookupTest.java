package dev.aymen.etaye.identity;

import dev.aymen.etaye.AbstractPostgresTest;
import dev.aymen.etaye.identity.dto.AuthenticatedAccount;
import dev.aymen.etaye.identity.dto.MembershipGrant;
import dev.aymen.etaye.identity.repository.IdentityLookupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
public class IdentityLookupTest extends AbstractPostgresTest {

    @Autowired
    private IdentityLookupRepository identityLookupRepository;

    @Autowired
    @Qualifier("identityDataSource")
    private DataSource identityDataSource;

    @Autowired
    @Qualifier("jdbcTemplate")
    private JdbcTemplate runtimeJdbcTemplate;

    @Test
    @DisplayName("Identity: Resolves account profile and active memberships with contract capabilities")
    void shouldResolveAccountAndMemberships() {
        UUID practiceId = createPracticeAsOwner("Cabinet Dentaire Alpha");
        String email = "dr.martin." + UUID.randomUUID() + "@example.com";
        UUID accountId = createAccountAsOwner(email, "Dr. Martin", generateRandomPasswordHash());

        String[] caps = new String[]{"CLINICAL_READ", "CLINICAL_WRITE", "PATIENT_REGISTER"};
        UUID membershipId = createMembershipAsOwner(accountId, practiceId, "ACTIVE", caps);

        Optional<AuthenticatedAccount> resolved = identityLookupRepository.resolveAccountAndMemberships(accountId);
        assertThat(resolved).isPresent();

        AuthenticatedAccount auth = resolved.get();
        assertThat(auth.account().id()).isEqualTo(accountId);
        assertThat(auth.account().normalizedEmail()).isEqualTo(email);
        assertThat(auth.account().displayName()).isEqualTo("Dr. Martin");
        assertThat(auth.account().isActive()).isTrue();

        assertThat(auth.memberships()).hasSize(1);
        MembershipGrant grant = auth.memberships().get(0);
        assertThat(grant.id()).isEqualTo(membershipId);
        assertThat(grant.practiceId()).isEqualTo(practiceId);
        assertThat(grant.capabilities()).containsExactlyInAnyOrder("CLINICAL_READ", "CLINICAL_WRITE", "PATIENT_REGISTER");

        assertThat(auth.hasCapabilityInPractice(practiceId, "CLINICAL_WRITE")).isTrue();
        assertThat(auth.hasCapabilityInPractice(practiceId, "SCHEDULE_MANAGE")).isFalse();
    }

    @Test
    @DisplayName("Identity: Column-level grant enforces password_hash is NEVER selectable under etaye_identity role")
    void shouldDenyPasswordHashSelectionOnIdentityRole() {
        assertThatThrownBy(() -> {
            try (Connection conn = identityDataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT password_hash FROM account")) {
                if (rs.next()) {
                    rs.getString(1);
                }
            }
        }).isInstanceOf(Exception.class)
                .hasMessageFindingMatch("(?i).*permission denied.*(?:table|relation|column).*account.*");
    }

    @Test
    @DisplayName("Identity: Runtime role has ZERO access to account or membership tables")
    void shouldDenyAccountAndMembershipAccessOnRuntimeRole() {
        assertThatThrownBy(() -> runtimeJdbcTemplate.queryForList("SELECT id FROM account"))
                .isInstanceOf(DataAccessException.class)
                .getRootCause()
                .hasMessageFindingMatch("(?i).*permission denied.*(?:table|relation).*account.*");

        assertThatThrownBy(() -> runtimeJdbcTemplate.queryForList("SELECT id FROM membership"))
                .isInstanceOf(DataAccessException.class)
                .getRootCause()
                .hasMessageFindingMatch("(?i).*permission denied.*(?:table|relation).*membership.*");
    }

    @Test
    @DisplayName("B4: Without etaye.account_id setting, etaye_identity role sees zero rows in account and membership")
    void shouldSeeZeroRowsWithoutAccountIdSetting() throws Exception {
        UUID practiceId = createPracticeAsOwner("Cabinet Dentaire RLS");
        UUID accountId = createAccountAsOwner("rls.test." + UUID.randomUUID() + "@example.com", "Dr. RLS");
        createMembershipAsOwner(accountId, practiceId, "ACTIVE", new String[]{"CLINICAL_READ"});

        try (Connection conn = identityDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // No etaye.account_id setting: RLS must hide all rows
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM account")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(0);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM membership")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("B4: With etaye.account_id set, etaye_identity role sees only that account and its memberships")
    void shouldSeeOnlyTargetAccountAndMembershipsWithAccountIdSetting() throws Exception {
        UUID practiceId = createPracticeAsOwner("Cabinet Dentaire RLS Isolation");
        UUID account1 = createAccountAsOwner("acc1." + UUID.randomUUID() + "@example.com", "Account 1", generateRandomPasswordHash());
        UUID membership1 = createMembershipAsOwner(account1, practiceId, "ACTIVE", new String[]{"CLINICAL_READ"});

        UUID account2 = createAccountAsOwner("acc2." + UUID.randomUUID() + "@example.com", "Account 2", generateRandomPasswordHash());
        UUID membership2 = createMembershipAsOwner(account2, practiceId, "ACTIVE", new String[]{"CLINICAL_WRITE"});

        try (Connection conn = identityDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Set context for account1
                try (java.sql.PreparedStatement ps = conn.prepareStatement("SELECT set_config('etaye.account_id', ?, true)")) {
                    ps.setString(1, account1.toString());
                    ps.execute();
                }

                // Query account: sees account1, does NOT see account2
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT id FROM account")) {
                    java.util.List<UUID> visibleAccounts = new java.util.ArrayList<>();
                    while (rs.next()) {
                        visibleAccounts.add((UUID) rs.getObject("id"));
                    }
                    assertThat(visibleAccounts).containsExactly(account1);
                }

                // Query membership: sees membership1, does NOT see membership2
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT id FROM membership")) {
                    java.util.List<UUID> visibleMemberships = new java.util.ArrayList<>();
                    while (rs.next()) {
                        visibleMemberships.add((UUID) rs.getObject("id"));
                    }
                    assertThat(visibleMemberships).containsExactly(membership1);
                }
            } finally {
                conn.rollback();
            }
        }
    }
}
