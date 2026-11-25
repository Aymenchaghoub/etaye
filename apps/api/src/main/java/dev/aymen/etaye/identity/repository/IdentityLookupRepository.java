package dev.aymen.etaye.identity.repository;

import dev.aymen.etaye.identity.dto.AccessContext;
import dev.aymen.etaye.identity.dto.AccountProfile;
import dev.aymen.etaye.identity.dto.AuthenticatedAccount;
import dev.aymen.etaye.identity.dto.MembershipGrant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Reads identity rows and the narrow current-grant projection via identityDataSource.
 * Column-level grants guarantee that password_hash can never be selected.
 * RLS enforces that queries see only the row of the account set by transaction-local
 * set_config('etaye.account_id', ?, true).
 */
@Repository
public class IdentityLookupRepository {

    private static final Logger log = LoggerFactory.getLogger(IdentityLookupRepository.class);

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
                } catch (Exception ex) {
                    log.warn("Rollback failed in identity context: class={}, code={}",
                            ex.getClass().getName(), "IDENTITY_ROLLBACK_FAILED");
                }
                if (e instanceof java.sql.SQLException sqlEx) {
                    throw sqlEx;
                }
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new org.springframework.dao.DataAccessResourceFailureException(
                        "Database unavailable during identity query", e);
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (Exception ex) {
                    log.warn("Reset autocommit failed in identity context: class={}, code={}",
                            ex.getClass().getName(), "IDENTITY_SET_AUTOCOMMIT_FAILED");
                }
            }
        });
    }

    public Optional<AccountProfile> findAccountById(UUID accountId) {
        return executeInAccountContext(accountId, conn -> findAccount(conn, accountId));
    }

    public List<MembershipGrant> findMembershipsByAccountId(UUID accountId) {
        return executeInAccountContext(accountId, conn -> findMemberships(conn, accountId));
    }

    public Optional<AuthenticatedAccount> resolveAccountAndMemberships(UUID accountId) {
        return executeInAccountContext(accountId, conn -> {
            var profile = findAccount(conn, accountId);
            if (profile.isEmpty()) {
                return Optional.empty();
            }

            var membershipIds = new ArrayList<UUID>();
            var portalLinkIds = new ArrayList<UUID>();
            var contexts = new ArrayList<AccessContext>();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT grant_id, grant_type, practice_id, practice_name, display_role, practice_timezone
                    FROM public.current_access_contexts(?) ORDER BY grant_id, grant_type
                    """)) {
                ps.setObject(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID id = rs.getObject("grant_id", UUID.class);
                        var kind = AccessContext.Kind.valueOf(rs.getString("grant_type"));
                        contexts.add(new AccessContext(kind, id, rs.getObject("practice_id", UUID.class),
                                rs.getString("practice_name"),
                                AccessContext.DisplayRole.valueOf(rs.getString("display_role")),
                                rs.getString("practice_timezone")));
                        if (kind == AccessContext.Kind.MEMBERSHIP) {
                            membershipIds.add(id);
                        } else {
                            portalLinkIds.add(id);
                        }
                    }
                }
            }
            return Optional.of(new AuthenticatedAccount(profile.get(), findMemberships(conn, accountId),
                    List.copyOf(membershipIds), List.copyOf(portalLinkIds), List.copyOf(contexts)));
        });
    }

    private Optional<AccountProfile> findAccount(Connection conn, UUID accountId) throws java.sql.SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT id, normalized_email, display_name, state, locale, version, created_at, updated_at,
                       credential_revision FROM public.account WHERE id = ?
                """)) {
            ps.setObject(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new AccountProfile(
                            rs.getObject("id", UUID.class),
                            rs.getString("normalized_email"),
                            rs.getString("display_name"),
                            rs.getString("state"),
                            rs.getString("locale"),
                            rs.getLong("version"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant(),
                            rs.getLong("credential_revision")));
                }
            }
        }
        return Optional.empty();
    }

    private List<MembershipGrant> findMemberships(Connection conn, UUID accountId) throws java.sql.SQLException {
        var memberships = new ArrayList<MembershipGrant>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT id, account_id, practice_id, state, capabilities FROM public.membership WHERE account_id = ?
                """)) {
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
                            rs.getObject("id", UUID.class),
                            rs.getObject("account_id", UUID.class),
                            rs.getObject("practice_id", UUID.class),
                            rs.getString("state"),
                            capabilities));
                }
            }
        }
        return memberships;
    }
}
