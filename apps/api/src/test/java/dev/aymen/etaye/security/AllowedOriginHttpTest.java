package dev.aymen.etaye.security;

import dev.aymen.etaye.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "etaye.web.allowed-origins=https://browser.example.test")
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

        var rejected = client.options().uri("/api/v1/meta")
                .header(HttpHeaders.ORIGIN, "https://browser.example.test:8443")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .retrieve().onStatus(HttpStatus.FORBIDDEN::equals, (req, res) -> {}).toBodilessEntity();
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rejected.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }
}
