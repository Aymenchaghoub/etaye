package dev.aymen.etaye.communications.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Communications module domain model representing an outbox event.
 * In accordance with AD-2, outbox processing belongs to the communications module.
 */
public class OutboxEntry {

    private final UUID id;
    private final UUID practiceId;
    private final String eventType;
    private final String payload;
    private final Instant createdAt;

    public OutboxEntry(UUID practiceId, String eventType, String payload) {
        this.id = UUID.randomUUID();
        this.practiceId = Objects.requireNonNull(practiceId, "practiceId must not be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPracticeId() {
        return practiceId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
