package dev.aymen.etaye.audit.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Audit module domain model representing an audit event.
 * In accordance with AD-2, audit processing belongs to the audit module.
 */
public class AuditEvent {

    private final UUID id;
    private final UUID practiceId;
    private final UUID actorAccountId;
    private final String eventType;
    private final String resourceType;
    private final UUID resourceId;
    private final Long resourceVersion;
    private final String outcome;
    private final Instant occurredAt;
    private final String correlationId;
    private final String safeMetadata;

    public AuditEvent(UUID practiceId, UUID actorAccountId, String eventType,
                      String resourceType, UUID resourceId, Long resourceVersion,
                      String outcome, String correlationId, String safeMetadata) {
        this.id = UUID.randomUUID();
        this.practiceId = practiceId;
        this.actorAccountId = actorAccountId;
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType must not be null");
        this.resourceId = resourceId;
        this.resourceVersion = resourceVersion;
        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        this.occurredAt = Instant.now();
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
        this.safeMetadata = Objects.requireNonNull(safeMetadata, "safeMetadata must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getPracticeId() {
        return practiceId;
    }

    public UUID getActorAccountId() {
        return actorAccountId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public Long getResourceVersion() {
        return resourceVersion;
    }

    public String getOutcome() {
        return outcome;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getSafeMetadata() {
        return safeMetadata;
    }
}
