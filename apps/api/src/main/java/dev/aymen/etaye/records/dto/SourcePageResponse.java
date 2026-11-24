package dev.aymen.etaye.records.dto;

import java.util.List;

public record SourcePageResponse(List<SourceResponse> items, String nextCursor) {
}
