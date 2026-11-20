package dev.aymen.etaye.security;

import dev.aymen.etaye.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"etaye.web.allowed-origins=https://browser.example.test",
                "etaye.compatibility.minimum-client-version=0.1.0", "etaye.compatibility.latest-client-version=0.2.0-beta.1"})
class AllowedOriginHttpTest extends AbstractPostgresTest {

    @LocalServerPort
    private int port;

    @Test
    void onlyTheExactConfiguredOriginCanPreflightWithoutCredentials() {
        var client = RestClient.create("http://localhost:" + port);
        var allowed = client.options().uri("/api/v1/meta")
                .header(HttpHeaders.ORIGIN, "https://browser.example.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Client-Version")
                .retrieve().toBodilessEntity();
        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allowed.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("https://browser.example.test");
        assertThat(allowed.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();

        var actual = client.get().uri("/api/v1/meta").header(HttpHeaders.ORIGIN, "https://browser.example.test")
                .retrieve().toEntity(Map.class);
        assertThat(actual.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(actual.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("https://browser.example.test");
        assertThat(actual.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
        assertThat(actual.getBody()).containsEntry("minimumClientVersion", "0.1.0")
                .containsEntry("latestClientVersion", "0.2.0-beta.1");

        var bearer = client.get().uri("/api/v1/meta").header(HttpHeaders.ORIGIN, "https://browser.example.test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-fixture")
                .retrieve().onStatus(HttpStatus.FORBIDDEN::equals, (req, res) -> {}).toEntity(Map.class);
        assertThat(bearer.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(bearer.getBody()).containsEntry("code", "FORBIDDEN");
        assertThat(bearer.getHeaders().getVary()).contains(
                "Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers");
        assertThat(bearer.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
        assertThat(bearer.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("https://browser.example.test");

        for (String origin : new String[]{"https://browser.example.test:8443", "https://browser.example.test/"}) {
            var rejected = client.options().uri("/api/v1/meta")
                    .header(HttpHeaders.ORIGIN, origin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                    .retrieve().onStatus(HttpStatus.FORBIDDEN::equals, (req, res) -> {}).toBodilessEntity();
            assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(rejected.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        }
    }
}
