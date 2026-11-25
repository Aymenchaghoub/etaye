package dev.aymen.etaye.records.dto;

import java.util.List;

/** The revisions of one measurement lineage, oldest first. */
public record MeasurementRevisionListResponse(List<MeasurementResponse> items) {
}
