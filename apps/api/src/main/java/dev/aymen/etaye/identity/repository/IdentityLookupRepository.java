package dev.aymen.etaye.identity.repository;

import dev.aymen.etaye.identity.domain.AccountProfile;
import dev.aymen.etaye.identity.domain.AuthenticatedAccount;
import dev.aymen.etaye.identity.domain.MembershipGrant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Dedicated bootstrap identity repository operating strictly under the restricted
 * etaye_identity role.
 * Queries only account and membership tables via identityDataSource.
 * Column-level grants guarantee that password_hash can never be selected.
 */
@Repository
public class IdentityLookupRepository {

    private final JdbcTemplate jdbcTemplate;

    public IdentityLookupRepository(@Qualifier("identityJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<AccountProfile> accountRowMapper = (rs, rowNum) -> new AccountProfile(
            (UUID) rs.getObject("id"),
            rs.getString("normalized_email"),
            rs.getString("display_name"),
            rs.getString("state"),
            rs.getString("locale")
    );

    private final RowMapper<MembershipGrant> membershipRowMapper = (rs, rowNum) -> {
        Array capsArray = rs.getArray("capabilities");
        Set<String> capabilities = Collections.emptySet();
        if (capsArray != null) {
            String[] caps = (String[]) capsArray.getArray();
            capabilities = new HashSet<>(Arrays.asList(caps));
        }
        return new MembershipGrant(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("account_id"),
                (UUID) rs.getObject("practice_id"),
                rs.getString("state"),
                capabilities
        );
    };

    public Optional<AccountProfile> findAccountById(UUID accountId) {
        String sql = "SELECT id, normalized_email, display_name, state, locale FROM account WHERE id = ?";
        List<AccountProfile> results = jdbcTemplate.query(sql, accountRowMapper, accountId);
        return results.stream().findFirst();
    }

    public Optional<AccountProfile> findAccountByEmail(String normalizedEmail) {
        String sql = "SELECT id, normalized_email, display_name, state, locale FROM account WHERE normalized_email = ?";
        List<AccountProfile> results = jdbcTemplate.query(sql, accountRowMapper, normalizedEmail);
        return results.stream().findFirst();
    }

    public List<MembershipGrant> findMembershipsByAccountId(UUID accountId) {
        String sql = "SELECT id, account_id, practice_id, state, capabilities FROM membership WHERE account_id = ?";
        return jdbcTemplate.query(sql, membershipRowMapper, accountId);
    }

    public Optional<AuthenticatedAccount> resolveAccountAndMemberships(UUID accountId) {
        return findAccountById(accountId).map(profile -> {
            List<MembershipGrant> memberships = findMembershipsByAccountId(accountId);
            return new AuthenticatedAccount(profile, memberships);
        });
    }
}
