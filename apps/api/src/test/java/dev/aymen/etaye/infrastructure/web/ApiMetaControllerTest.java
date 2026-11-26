package dev.aymen.etaye.infrastructure.web;

import dev.aymen.etaye.infrastructure.demo.DemoFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ApiMetaControllerTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.0.1x", "01.0.0", "0.1.0-01", "0.1.0-rc.01+build", "0.1.0-rc..1", "0.1.0+build."})
    void refusesInvalidConfiguredSemver(String invalid) {
        var fixture = new StaticListableBeanFactory().getBeanProvider(DemoFixture.class);
        assertThatIllegalArgumentException().isThrownBy(() -> new ApiMetaController(invalid, "0.1.0", fixture));
        assertThatIllegalArgumentException().isThrownBy(() -> new ApiMetaController("0.1.0", invalid, fixture));
    }

    @Test
    void preservesLongConfiguredVersionsInMetadata() {
        var fixture = new StaticListableBeanFactory().getBeanProvider(DemoFixture.class);
        String prerelease = "1.2.3-rc." + "01x.".repeat(10_000) + "0";
        String build = "1.2.3+" + "001.".repeat(10_000) + "build";

        var metadata = new ApiMetaController(prerelease, build, fixture).getApiMeta().getBody();

        assertThat(metadata).isNotNull();
        assertThat(metadata.minimumClientVersion()).isEqualTo(prerelease);
        assertThat(metadata.latestClientVersion()).isEqualTo(build);
    }
}
