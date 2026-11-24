package dev.aymen.etaye.records.domain;

import java.time.Instant;
import java.util.UUID;

/** One current statement. Source fields are empty for the only statements that may stand without a source. */
public record AssertionView(
        UUID id,
        UUID practiceId,
        AssertionCategory category,
        AssertionKind kind,
        String text,
        EventDateView eventDate,
        Instant recordedAt,
        int revision,
        UUID supersedesId,
        String correctionReason,
        UUID sourceRevisionId,
        String sourceReference,
        SourceKind sourceKind,
        SourceType sourceType,
        String authorLabel,
        Instant createdAt
) {
}
