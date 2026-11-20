package dev.aymen.etaye.identity.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AccountAccessRepository {

    private final JdbcTemplate jdbc;

    public AccountAccessRepository(@Qualifier("identityJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Credential> findCredential(String email) {
        return jdbc.query("SELECT * FROM public.find_sign_in_credential(?)",
                (row, i) -> new Credential(row.getObject("account_id", UUID.class),
                        row.getString("encoded_password"), row.getLong("credential_revision")), email)
                .stream().findFirst();
    }

    public boolean recordSignIn(UUID accountId, long revision, String correlationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT public.record_account_sign_in(?, ?, ?)",
                Boolean.class, accountId, revision, correlationId));
    }

    public Optional<Instant> startRecovery(String email, String receiptDigest, String tokenDigest) {
        // This standalone statement commits before control returns to the delivery boundary.
        return Optional.ofNullable(jdbc.queryForObject("SELECT public.start_account_recovery(?, ?, ?)",
                (row, i) -> row.getObject(1, OffsetDateTime.class), email, receiptDigest, tokenDigest))
                .map(OffsetDateTime::toInstant);
    }

    public boolean redeemRecovery(String tokenDigest, String newPasswordHash, String correlationId) {
        return jdbc.queryForObject("SELECT public.redeem_account_recovery(?, ?, ?)",
                UUID.class, tokenDigest, newPasswordHash, correlationId) != null;
    }

    public record Credential(UUID accountId, String encodedPassword, long revision) {
        @Override
        public String toString() {
            return "Credential[accountId=" + accountId + ", revision=" + revision + "]";
        }
    }
}
