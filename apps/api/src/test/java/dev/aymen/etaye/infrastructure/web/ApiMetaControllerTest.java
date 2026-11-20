package dev.aymen.etaye.infrastructure.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ApiMetaControllerTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.0.1x", "01.0.0", "0.1.0-01"})
    void refusesInvalidConfiguredSemver(String invalid) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ApiMetaController(invalid, "0.1.0"));
        assertThatIllegalArgumentException().isThrownBy(() -> new ApiMetaController("0.1.0", invalid));
    }
}
