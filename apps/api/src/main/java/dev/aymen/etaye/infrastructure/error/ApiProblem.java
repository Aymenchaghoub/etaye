package dev.aymen.etaye.infrastructure.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.util.List;

/**
 * RFC 9457 compliant problem details object matching the OpenAPI Problem schema.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiProblem(
        @JsonProperty("type") URI type,
        @JsonProperty("title") String title,
        @JsonProperty("status") int status,
        @JsonProperty("detail") String detail,
        @JsonProperty("instance") String instance,
        @JsonProperty("code") String code,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("fieldErrors") List<FieldErrorItem> fieldErrors,
        @JsonProperty("candidates") List<?> candidates
) {
    public static ApiProblem of(int status, String title, String code, String detail, String correlationId) {
        return new ApiProblem(URI.create("about:blank"), title, status, detail, null, code, correlationId, null, null);
    }

    public static ApiProblem withFieldErrors(int status, String title, String code, String detail, String correlationId, List<FieldErrorItem> fieldErrors) {
        return new ApiProblem(URI.create("about:blank"), title, status, detail, null, code, correlationId, fieldErrors, null);
    }

    public static ApiProblem withCandidates(int status, String title, String code, String detail, String correlationId, List<?> candidates) {
        return new ApiProblem(URI.create("about:blank"), title, status, detail, null, code, correlationId, null, candidates);
    }
}
