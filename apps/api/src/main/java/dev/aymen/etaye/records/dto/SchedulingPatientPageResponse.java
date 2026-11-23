package dev.aymen.etaye.records.dto;

import java.util.List;

public record SchedulingPatientPageResponse(List<SchedulingPatientResponse> items, String nextCursor) {
}
