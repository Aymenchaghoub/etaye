package dev.aymen.etaye.records.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.aymen.etaye.records.domain.AssertionView;

import java.time.Instant;
import java.util.UUID;

/**
 * A statement as the contract defines it. UNKNOWN and NOT_ASKED statements may carry no text, no source and no
 * correction: the absent fields are omitted and never serialized as an empty value that could read as a negative.
 * Statements are immutable, so the contract's aggregate version is the revision number.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssertionResponse(
        UUID id,
        long version,
        Instant createdAt,
        Instant updatedAt,
        UUID practiceId,
        String category,
        String assertionKind,
        String text,
        EventDateResponse eventDate,
        String sourceType,
        String correctionReason,
        UUID sourceRevisionId,
        Instant recordedAt,
        String authorLabel,
        String sourceReference,
        String sourceKind,
        int revision,
        UUID supersedesId
) {
    public static AssertionResponse of(AssertionView view) {
        return new AssertionResponse(view.id(), view.revision(), view.createdAt(), view.createdAt(), view.practiceId(),
                view.category().name(), view.kind().name(), view.text(), EventDateResponse.of(view.eventDate()),
                view.sourceType() == null ? null : view.sourceType().name(), view.correctionReason(),
                view.sourceRevisionId(), view.recordedAt(), view.authorLabel(), view.sourceReference(),
                view.sourceKind() == null ? null : view.sourceKind().name(), view.revision(),
                view.supersedesId());
    }
}
