package dev.aymen.etaye.records.domain;

import java.time.Instant;
import java.util.UUID;

/** One immutable source revision as read from the evidence table. */
public record SourceView(
        UUID id,
        UUID practiceId,
        UUID recordId,
        int revision,
        String reference,
        SourceKind kind,
        String label,
        String excerpt,
        SourceType sourceType,
        EventDateView eventDate,
        Instant recordedAt,
        String authorLabel,
        String locator,
        UUID supersedesId,
        String supersedesReference,
        String correctionReason
) {
}
