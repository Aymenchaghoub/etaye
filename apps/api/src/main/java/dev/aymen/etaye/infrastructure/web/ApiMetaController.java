package dev.aymen.etaye.infrastructure.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiMetaController {

    private final ApiMeta metadata;

    public ApiMetaController(
            @Value("${etaye.compatibility.minimum-client-version:0.0.1}") String minimumClientVersion,
            @Value("${etaye.compatibility.latest-client-version:0.0.1}") String latestClientVersion) {
        metadata = new ApiMeta("v1", minimumClientVersion, latestClientVersion);
    }

    @GetMapping(value = "/api/v1/meta", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiMeta> getApiMeta() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(metadata);
    }

    public record ApiMeta(String apiVersion, String minimumClientVersion, String latestClientVersion) {}
}
