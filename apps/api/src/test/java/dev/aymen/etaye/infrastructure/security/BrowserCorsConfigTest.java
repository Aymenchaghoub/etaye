package dev.aymen.etaye.infrastructure.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BrowserCorsConfigTest {

    @ParameterizedTest
    @ValueSource(strings = {"https://browser.example.test:443", "http://browser.example.test:80"})
    void rejectsExplicitDefaultPorts(String origin) {
        assertThatIllegalArgumentException().isThrownBy(() -> new BrowserCorsConfig().corsConfigurationSource(origin));
    }
}
