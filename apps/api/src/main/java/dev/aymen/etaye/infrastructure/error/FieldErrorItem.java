package dev.aymen.etaye.infrastructure.error;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FieldErrorItem(
        @JsonProperty("field") String field,
        @JsonProperty("code") String code,
        @JsonProperty("message") String message
) {}
