package dev.aymen.etaye.infrastructure.idempotency;

import dev.aymen.etaye.infrastructure.error.IdempotencyConflictException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String computeSha256(String input) {
        if (input == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public Optional<UUID> checkReceipt(UUID practiceId, UUID actorId, String operation,
                                       String keyDigest, String bodyHash) {
        Objects.requireNonNull(practiceId, "practiceId must not be null");
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(keyDigest, "keyDigest must not be null");
        Objects.requireNonNull(bodyHash, "bodyHash must not be null");

        try {
            return jdbcTemplate.queryForObject("""
                SELECT body_hash, response_resource
                FROM idempotency_receipt
                WHERE practice_id = ? AND actor_id = ? AND operation = ? AND key_digest = ?
                """,
                (rs, rowNum) -> {
                    String existingBodyHash = rs.getString("body_hash");
                    if (!Objects.equals(existingBodyHash, bodyHash)) {
                        throw new IdempotencyConflictException("Idempotency key replayed with different request body");
                    }
                    String resourceStr = rs.getString("response_resource");
                    return Optional.ofNullable(resourceStr != null ? UUID.fromString(resourceStr) : null);
                },
                practiceId, actorId, operation, keyDigest
            );
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void recordReceipt(UUID practiceId, UUID actorId, String operation,
                              String keyDigest, String bodyHash, UUID responseResource) {
        Objects.requireNonNull(practiceId, "practiceId must not be null");
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(keyDigest, "keyDigest must not be null");
        Objects.requireNonNull(bodyHash, "bodyHash must not be null");

        jdbcTemplate.update("""
            INSERT INTO idempotency_receipt (
                id, practice_id, actor_id, operation,
                key_digest, body_hash, response_resource,
                outcome, expires_at
            ) VALUES (
                gen_random_uuid(), ?, ?, ?,
                ?, ?, ?,
                'CREATED', CURRENT_TIMESTAMP + INTERVAL '24 hours'
            )
            """,
            practiceId,
            actorId,
            operation,
            keyDigest,
            bodyHash,
            responseResource
        );
    }
}
