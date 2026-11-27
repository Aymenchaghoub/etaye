package dev.aymen.etaye.records.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.aymen.etaye.records.domain.EventDateView;
import dev.aymen.etaye.records.domain.LabResultView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LabResultResponse(
        UUID id, int version, Instant createdAt, Instant updatedAt, UUID practiceId,
        String analyte, String valueKind, String numericValue, String qualitativeValue, String comparator, String unit,
        String lowerBound, String upperBound, String referenceIntervalText, String intervalSource,
        String intervalUnit, String intervalContext, String outsideInterval, Boolean unconfirmedTranscription,
        Instant specimenAt, Instant resultAt, EventDateResponse specimenDate, EventDateResponse resultDate,
        EventDateResponse eventDate, String sourceType, String correctionReason, UUID sourceRevisionId,
        UUID supersedesId, Instant recordedAt, String authorLabel, String sourceReference, int revision,
        LabResultView.ComparisonContext comparisonContext
) {
    public static LabResultResponse of(LabResultView result) {
        EventDateView specimen = result.specimenDate();
        EventDateView report = result.resultDate();
        EventDateView event = specimen.precision() == EventDateView.Precision.UNKNOWN ? report : specimen;
        return new LabResultResponse(result.id(), result.revision(), result.createdAt(), result.createdAt(), result.practiceId(),
                result.analyte(), result.numericValue() == null ? "QUALITATIVE" : "NUMERIC",
                decimal(result.numericValue()), result.qualitativeValue(), result.comparator(), result.unit(),
                decimal(result.lowerBound()), decimal(result.upperBound()), result.intervalText(), result.intervalSource(),
                result.intervalUnit(), result.intervalContext(), result.outsideInterval(),
                result.unconfirmedTranscription() ? Boolean.TRUE : null,
                specimen.instant(), report.instant(), EventDateResponse.of(specimen), EventDateResponse.of(report),
                EventDateResponse.of(event), result.sourceType(), result.correctionReason(), result.sourceRevisionId(),
                result.supersedesId(), result.recordedAt(), result.authorLabel(), result.sourceReference(),
                result.revision(), result.comparisonContext());
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
