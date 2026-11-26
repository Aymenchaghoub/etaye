package dev.aymen.etaye.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BrowserCorsConfigTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "https://browser.example.test:443",
        "http://browser.example.test:80",
        "ftp://browser.example.test",
        "https://browser.example.test/path",
        "https://browser.example.test?query=1",
        "https://browser.example.test#frag",
        "https://user@browser.example.test",
        "https://browser.example.test:0",
        "https://browser.example.test:70000",
        "https://",
        "not a valid uri"
    })
    void rejectsInvalidOrNonExactOrigins(String origin) {
        assertThatIllegalArgumentException().isThrownBy(() -> new BrowserCorsConfig().corsConfigurationSource(origin));
    }

    @Test
    void checksConfiguredOriginsStrictlyWithoutTrailingSlash() {
        var source = new BrowserCorsConfig().corsConfigurationSource("https://app.example.test, http://localhost:5173 ");
        var config = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/meta"));

        assertThat(config).isNotNull();
        assertThat(config.checkOrigin("https://app.example.test")).isEqualTo("https://app.example.test");
        assertThat(config.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
        assertThat(config.checkOrigin("https://app.example.test/")).isNull();
        assertThat(config.checkOrigin("https://evil.example.test")).isNull();
    }
}
