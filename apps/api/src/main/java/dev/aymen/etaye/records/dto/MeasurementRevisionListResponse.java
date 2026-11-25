package dev.aymen.etaye.records.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The revisions of one measurement lineage, oldest first. A lineage longer than the response keeps its original revision
 * and its newest ones, and says so; the flag is absent when the whole lineage is listed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeasurementRevisionListResponse(List<MeasurementResponse> items, Boolean truncated) {
}
