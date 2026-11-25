package dev.aymen.etaye.records.dto;

import java.util.List;

public record MeasurementPageResponse(List<MeasurementResponse> items, String nextCursor) {
}
