package dev.aymen.etaye.communications.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class OutboxService {

    private final JdbcTemplate jdbcTemplate;

    public OutboxService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void publish(UUID practiceId, String eventType, UUID resourceId,
                        Long resourceVersion, String dedupeKey, String safePayloadJson) {
        Objects.requireNonNull(practiceId, "practiceId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(resourceId, "resourceId must not be null");
        Objects.requireNonNull(resourceVersion, "resourceVersion must not be null");
        Objects.requireNonNull(dedupeKey, "dedupeKey must not be null");

        jdbcTemplate.update("""
            INSERT INTO outbox_event (
                id, practice_id, event_type, resource_id,
                resource_version, dedupe_key, safe_payload
            ) VALUES (
                gen_random_uuid(), ?, ?, ?,
                ?, ?, ?::jsonb
            )
            """,
            practiceId,
            eventType,
            resourceId,
            resourceVersion,
            dedupeKey,
            safePayloadJson != null && !safePayloadJson.isBlank() ? safePayloadJson : "{}"
        );
    }
}
