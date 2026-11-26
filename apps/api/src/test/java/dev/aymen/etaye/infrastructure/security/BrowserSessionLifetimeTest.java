package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.MutableTestClock;
import dev.aymen.etaye.identity.dto.AccountProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BrowserSessionLifetimeTest {

    private final MutableTestClock clock = new MutableTestClock(Instant.now());

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private BrowserSessionLifetime newLifetime(Duration idle, Duration absolute) {
        var beans = new StaticListableBeanFactory();
        beans.addBean("clock", clock);
        return new BrowserSessionLifetime(idle, absolute, beans.getBeanProvider(Clock.class), new HttpSessionCsrfTokenRepository());
    }

    @Test
    void validatesConfiguredIdleAndAbsoluteBounds() {
        assertThatIllegalArgumentException().isThrownBy(() -> newLifetime(Duration.ZERO, Duration.ofHours(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> newLifetime(Duration.ofMinutes(31), Duration.ofHours(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> newLifetime(Duration.ofMinutes(10), Duration.ofMinutes(5)));
        assertThatIllegalArgumentException().isThrownBy(() -> newLifetime(Duration.ofMinutes(10), Duration.ofHours(9)));
    }

    @Test
    void managesAnonymousExpiryLegacySessionsAndActivityPolling() {
        var lifetime = newLifetime(Duration.ofMinutes(5), Duration.ofHours(1));
        assertThat(lifetime.isExpiredAnonymous(null)).isFalse();
        assertThat(lifetime.isCurrent(null, 0L)).isFalse();

        // Anonymous session before and after 5-minute TTL
        var anonSession = new MockHttpSession();
        var anonReq = new MockHttpServletRequest("GET", "/api/v1/csrf");
        anonReq.setSession(anonSession);
        assertThat(lifetime.isExpiredAnonymous(anonSession)).isFalse();

        clock.advanceSeconds(305);
        assertThat(lifetime.isExpiredAnonymous(anonSession)).isTrue();
        lifetime.retireExpiredAnonymousSession(anonReq);
        assertThat(anonSession.isInvalid()).isTrue();

        // Anonymous session carrying an authenticated SecurityContext is never retired as anonymous
        var authAnonSession = new MockHttpSession();
        var secCtx = SecurityContextHolder.createEmptyContext();
        secCtx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("actor", null, List.of()));
        authAnonSession.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, secCtx);
        assertThat(lifetime.isExpiredAnonymous(authAnonSession)).isFalse();

        // Legacy session without signedInAt/credentialRevision falls back cleanly and polling /api/v1/session skips extension
        var legacySession = new MockHttpSession();
        legacySession.setMaxInactiveInterval(600);
        assertThat(lifetime.isCurrent(legacySession, 0L)).isTrue();
        assertThat(lifetime.isCurrent(legacySession, 1L)).isFalse();

        var pollGet = new MockHttpServletRequest("GET", "/api/v1/session");
        lifetime.recordActivity(pollGet, legacySession);
        var pollHead = new MockHttpServletRequest("HEAD", "/api/v1/session");
        lifetime.recordActivity(pollHead, legacySession);

        // Establish full session and verify absolute vs idle expiry clamp
        var req = new MockHttpServletRequest("POST", "/api/v1/session");
        var established = new MockHttpSession();
        req.setSession(established);
        var account = new AccountProfile(UUID.randomUUID(), "a@example.test", "A", "ACTIVE", "fr-FR", 0L,
                clock.instant(), clock.instant(), 3L);
        lifetime.establish(req, new MockHttpServletResponse(), account);
        assertThat(lifetime.isCurrent(established, 3L)).isTrue();
        clock.advanceSeconds(3605);
        assertThat(lifetime.isCurrent(established, 3L)).isFalse();
    }
}
