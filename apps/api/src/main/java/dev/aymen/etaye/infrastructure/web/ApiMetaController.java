package dev.aymen.etaye.infrastructure.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

@RestController
public class ApiMetaController {

    private static final String NUMBER = "(?:0|[1-9][0-9]*)";
    private static final String PRERELEASE_PART = "(?:" + NUMBER + "|[0-9]*[A-Za-z-][0-9A-Za-z-]*)";
    private static final Pattern SEMANTIC_VERSION = Pattern.compile(
            NUMBER + "\\." + NUMBER + "\\." + NUMBER
                    + "(?:-" + PRERELEASE_PART + "(?:\\." + PRERELEASE_PART + ")*)?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");

    private final ApiMeta metadata;

    public ApiMetaController(
            @Value("${etaye.compatibility.minimum-client-version:0.0.1}") String minimumClientVersion,
            @Value("${etaye.compatibility.latest-client-version:0.0.1}") String latestClientVersion) {
        if (!SEMANTIC_VERSION.matcher(minimumClientVersion).matches()
                || !SEMANTIC_VERSION.matcher(latestClientVersion).matches()) {
            throw new IllegalArgumentException("Configure client versions using SemVer 2.0.0");
        }
        metadata = new ApiMeta("v1", minimumClientVersion, latestClientVersion);
    }

    @GetMapping(value = "/api/v1/meta", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiMeta> getApiMeta() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(metadata);
    }

    public record ApiMeta(String apiVersion, String minimumClientVersion, String latestClientVersion) {}
}
