package dev.aymen.etaye.records.dto;

import java.time.Instant;
import java.util.List;

/** The three sections with their derived state; {@code asOf} is null for the current record. */
public record HistorySummaryResponse(Instant asOf, List<HistorySectionResponse> sections) {
}
