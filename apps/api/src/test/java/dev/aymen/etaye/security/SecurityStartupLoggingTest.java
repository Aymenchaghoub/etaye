package dev.aymen.etaye.security;

import dev.aymen.etaye.AbstractPostgresTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
public class SecurityStartupLoggingTest extends AbstractPostgresTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("A3: Application context has no UserDetailsService bean and logs contain no generated security password")
    void noUserDetailsServiceAndNoGeneratedPasswordLogged(CapturedOutput output) {
        assertThatThrownBy(() -> applicationContext.getBean(UserDetailsService.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);

        assertThat(output.getAll())
                .doesNotContain("generated security password")
                .doesNotContain("Using generated security password");
    }
}
