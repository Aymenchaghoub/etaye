package dev.aymen.etaye.records.dto;

import java.util.List;

public record TimelinePageResponse(List<TimelineItemResponse> items, String nextCursor) {
}
