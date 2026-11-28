package dev.aymen.etaye.records.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LabResultView(
        UUID id, UUID practiceId, int revision, String analyte,
        BigDecimal numericValue, String qualitativeValue, String comparator, String unit,
        BigDecimal lowerBound, BigDecimal upperBound, String intervalText, String intervalSource,
        String intervalUnit, String intervalContext, String outsideInterval, boolean unconfirmedTranscription,
        EventDateView specimenDate, EventDateView resultDate,
        Instant recordedAt, Instant createdAt, String authorLabel,
        UUID sourceRevisionId, String sourceReference, String sourceType,
        UUID supersedesId, String correctionReason, ComparisonContext comparisonContext
) {
    public record ComparisonContext(String method, String fasting, String samplingWindow) { }
}
