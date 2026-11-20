package dev.aymen.etaye.infrastructure.idempotency;

import dev.aymen.etaye.infrastructure.error.IdempotencyConflictException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
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

    /**
     * Computes the body hash from a canonical structured form using length-prefixed fields
     * in sorted key order:
     * (key_length:key:(NULL | VAL:val_length:val);)
     * Never uses a '|' join, and null strictly differs from empty string "".
     */
    public String computeCanonicalBodyHash(Map<String, Object> fields) {
        Objects.requireNonNull(fields, "fields must not be null");
        // Always the natural key order: a TreeMap built with another comparator must give the same hash
        Map<String, Object> sorted = new TreeMap<>(fields);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            sb.append(key.length()).append(':').append(key).append(':');
            if (val == null) {
                sb.append("NULL;");
            } else {
                String strVal = val.toString();
                sb.append("VAL:").append(strVal.length()).append(':').append(strVal).append(';');
            }
        }
        return computeSha256(sb.toString());
    }

    /**
     * Acquires a transaction-scoped advisory lock in PostgreSQL on a hash of
     * (actor, practice, operation, key_digest).
     * Serializes concurrent requests with the same idempotency key before reading the receipt.
     */
    public void acquireLock(UUID practiceId, UUID actorId, String operation, String keyDigest) {
        Objects.requireNonNull(practiceId, "practiceId must not be null");
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(keyDigest, "keyDigest must not be null");

        long lockKey = computeLockKey(practiceId, actorId, operation, keyDigest);
        jdbcTemplate.execute((Connection conn) -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                ps.setLong(1, lockKey);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {}
                }
            }
            return null;
        });
    }

    public long computeLockKey(UUID practiceId, UUID actorId, String operation, String keyDigest) {
        String input = actorId + ":" + practiceId + ":" + operation + ":" + keyDigest;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(hash).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Checks if a prior idempotency receipt exists.
     *
     * Decision on expiry: until a purge exists, a receipt is replayed whatever its expires_at
     * (never ignore the unique constraint and never answer 500); recorded as deferred work.
     */
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
        Objects.requireNonNull(responseResource, "responseResource must not be null for a CREATED receipt");

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
