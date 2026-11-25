package dev.aymen.etaye.records.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.aymen.etaye.records.domain.MeasurementView;

import java.time.Instant;
import java.util.UUID;

/**
 * A measurement as the contract defines it. Revisions are immutable, so the contract's aggregate version is the revision
 * number. The decimal value is serialized as the string the row stores (no rounding, no reformatting) and the unit as
 * entered; absent fields are omitted and never serialized as an empty value.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeasurementResponse(
        UUID id,
        long version,
        Instant createdAt,
        Instant updatedAt,
        UUID practiceId,
        String concept,
        String value,
        String unit,
        Instant observedAt,
        String sourceType,
        UUID groupId,
        String correctionReason,
        UUID sourceRevisionId,
        UUID supersedesId,
        Instant recordedAt,
        String authorLabel,
        String sourceReference,
        int revision
) {
    public static MeasurementResponse of(MeasurementView view) {
        return new MeasurementResponse(view.id(), view.revision(), view.createdAt(), view.createdAt(), view.practiceId(),
                view.concept(), view.value().toPlainString(), view.unit(), view.observedAt(), view.sourceType().name(),
                view.groupId(), view.correctionReason(), view.sourceRevisionId(), view.supersedesId(), view.recordedAt(),
                view.authorLabel(), view.sourceReference(), view.revision());
    }
}
