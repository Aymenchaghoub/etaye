package dev.aymen.etaye.records.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One immutable measurement revision as read from the measurement table, with the source revision that supports it. */
public record MeasurementView(
        UUID id,
        UUID practiceId,
        UUID recordId,
        int revision,
        String concept,
        BigDecimal value,
        String unit,
        Instant observedAt,
        Instant recordedAt,
        String authorLabel,
        UUID sourceRevisionId,
        String sourceReference,
        SourceType sourceType,
        UUID groupId,
        UUID supersedesId,
        String correctionReason,
        Instant createdAt
) {
}
