package dev.aymen.etaye.identity;

import dev.aymen.etaye.AbstractPostgresTest;
import dev.aymen.etaye.identity.domain.AuthenticatedAccount;
import dev.aymen.etaye.identity.domain.MembershipGrant;
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
        UUID accountId = createAccountAsOwner(email, "Dr. Martin", "argon2_secret_hash");

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
}
