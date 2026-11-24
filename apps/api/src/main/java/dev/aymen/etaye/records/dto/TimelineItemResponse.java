package dev.aymen.etaye.records.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.aymen.etaye.records.domain.SourceView;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimelineItemResponse(
        UUID id,
        EventDateResponse eventDate,
        Instant recordedAt,
        String kind,
        UUID sourceRevisionId,
        String label,
        boolean corrected,
        String reference,
        String sourceType,
        String authorLabel,
        int revision,
        String correctionReason,
        String excerpt
) {
    public static TimelineItemResponse of(SourceView view) {
        return new TimelineItemResponse(view.id(), EventDateResponse.of(view.eventDate()), view.recordedAt(),
                view.kind().name(), view.id(), view.label(), view.revision() > 1, view.reference(),
                view.sourceType().name(), view.authorLabel(), view.revision(), view.correctionReason(), view.excerpt());
    }
}
