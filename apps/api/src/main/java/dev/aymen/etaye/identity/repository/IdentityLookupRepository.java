package dev.aymen.etaye.identity.repository;

import dev.aymen.etaye.identity.domain.AccountProfile;
import dev.aymen.etaye.identity.domain.AuthenticatedAccount;
import dev.aymen.etaye.identity.domain.MembershipGrant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Dedicated bootstrap identity repository operating strictly under the restricted
 * etaye_identity role.
 * Queries only account and membership tables via identityDataSource.
 * Column-level grants guarantee that password_hash can never be selected.
 * RLS enforces that queries see only the row of the account set by transaction-local
 * set_config('etaye.account_id', ?, true).
 */
@Repository
public class IdentityLookupRepository {

    private final JdbcTemplate jdbcTemplate;

    public IdentityLookupRepository(@Qualifier("identityJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @FunctionalInterface
    private interface AccountConnectionCallback<T> {
        T doInConnection(Connection conn) throws Exception;
    }

    private <T> T executeInAccountContext(UUID accountId, AccountConnectionCallback<T> action) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        return jdbcTemplate.execute((Connection conn) -> {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                // Parameterized setting of transaction-local account context
                try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('etaye.account_id', ?, true)")) {
                    ps.setString(1, accountId.toString());
                    ps.execute();
                }
                T result = action.doInConnection(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (Exception ignored) {}
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException("Error executing identity query in account context", e);
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (Exception ignored) {}
            }
        });
    }

    public Optional<AccountProfile> findAccountById(UUID accountId) {
        return executeInAccountContext(accountId, conn -> {
            String sql = "SELECT id, normalized_email, display_name, state, locale FROM account WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new AccountProfile(
                                (UUID) rs.getObject("id"),
                                rs.getString("normalized_email"),
                                rs.getString("display_name"),
                                rs.getString("state"),
                                rs.getString("locale")
                        ));
                    }
                }
            }
            return Optional.empty();
        });
    }

    public List<MembershipGrant> findMembershipsByAccountId(UUID accountId) {
        return executeInAccountContext(accountId, conn -> {
            String sql = "SELECT id, account_id, practice_id, state, capabilities FROM membership WHERE account_id = ?";
            List<MembershipGrant> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Array capsArray = rs.getArray("capabilities");
                        Set<String> capabilities = Collections.emptySet();
                        if (capsArray != null) {
                            String[] caps = (String[]) capsArray.getArray();
                            capabilities = new HashSet<>(Arrays.asList(caps));
                        }
                        list.add(new MembershipGrant(
                                (UUID) rs.getObject("id"),
                                (UUID) rs.getObject("account_id"),
                                (UUID) rs.getObject("practice_id"),
                                rs.getString("state"),
                                capabilities
                        ));
                    }
                }
            }
            return list;
        });
    }

    public Optional<AuthenticatedAccount> resolveAccountAndMemberships(UUID accountId) {
        return executeInAccountContext(accountId, conn -> {
            AccountProfile profile = null;
            String sqlAcc = "SELECT id, normalized_email, display_name, state, locale FROM account WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlAcc)) {
                ps.setObject(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        profile = new AccountProfile(
                                (UUID) rs.getObject("id"),
                                rs.getString("normalized_email"),
                                rs.getString("display_name"),
                                rs.getString("state"),
                                rs.getString("locale")
                        );
                    }
                }
            }
            if (profile == null) {
                return Optional.empty();
            }

            List<MembershipGrant> memberships = new ArrayList<>();
            String sqlMem = "SELECT id, account_id, practice_id, state, capabilities FROM membership WHERE account_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlMem)) {
                ps.setObject(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Array capsArray = rs.getArray("capabilities");
                        Set<String> capabilities = Collections.emptySet();
                        if (capsArray != null) {
                            String[] caps = (String[]) capsArray.getArray();
                            capabilities = new HashSet<>(Arrays.asList(caps));
                        }
                        memberships.add(new MembershipGrant(
                                (UUID) rs.getObject("id"),
                                (UUID) rs.getObject("account_id"),
                                (UUID) rs.getObject("practice_id"),
                                rs.getString("state"),
                                capabilities
                        ));
                    }
                }
            }
            return Optional.of(new AuthenticatedAccount(profile, memberships));
        });
    }
}
