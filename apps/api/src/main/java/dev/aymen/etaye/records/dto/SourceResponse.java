package dev.aymen.etaye.records.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.aymen.etaye.records.domain.SourceView;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceResponse(
        UUID id,
        UUID practiceId,
        UUID recordId,
        String sourceType,
        EventDateResponse eventDate,
        Instant recordedAt,
        String authorLabel,
        int revision,
        String locator,
        UUID supersedesId,
        String reference,
        String kind,
        String label,
        String excerpt,
        String correctionReason,
        String supersedesReference
) {
    public static SourceResponse of(SourceView view) {
        return new SourceResponse(view.id(), view.practiceId(), view.recordId(), view.sourceType().name(),
                EventDateResponse.of(view.eventDate()), view.recordedAt(), view.authorLabel(), view.revision(),
                view.locator(), view.supersedesId(), view.reference(), view.kind().name(), view.label(),
                view.excerpt(), view.correctionReason(), view.supersedesReference());
    }
}
