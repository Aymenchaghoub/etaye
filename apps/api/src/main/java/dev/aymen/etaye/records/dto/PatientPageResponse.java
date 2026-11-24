package dev.aymen.etaye.records.dto;

import java.util.List;

public record PatientPageResponse(List<PatientResponse> items, String nextCursor) {
}
