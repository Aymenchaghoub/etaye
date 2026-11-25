package dev.aymen.etaye.records.dto;

import java.util.List;

public record AssertionPageResponse(List<AssertionResponse> items, String nextCursor) {
}
