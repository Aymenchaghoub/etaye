package dev.aymen.etaye.audit.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class AuditService {

    private final JdbcTemplate jdbcTemplate;

    public AuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordEvent(UUID practiceId, UUID actorAccountId, String eventType,
                            String resourceType, UUID resourceId, Long resourceVersion,
                            String outcome, String correlationId, String safeMetadataJson) {
        Objects.requireNonNull(practiceId, "practiceId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");

        jdbcTemplate.update("""
            INSERT INTO audit_event (
                id, practice_id, actor_account_id, event_type,
                resource_type, resource_id, resource_version,
                outcome, correlation_id, safe_metadata
            ) VALUES (
                gen_random_uuid(), ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?::jsonb
            )
            """,
            practiceId,
            actorAccountId,
            eventType,
            resourceType,
            resourceId,
            resourceVersion,
            outcome,
            correlationId,
            safeMetadataJson != null && !safeMetadataJson.isBlank() ? safeMetadataJson : "{}"
        );
    }
}
