package dev.aymen.etaye.records.dto;

import java.util.List;

public record HistorySectionResponse(String category, String state, List<AssertionResponse> statements, boolean truncated) {
}
