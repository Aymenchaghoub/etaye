package dev.aymen.etaye.infrastructure.security;

import dev.aymen.etaye.MutableTestClock;
import dev.aymen.etaye.infrastructure.security.AccessDenialAudit.Key;
import dev.aymen.etaye.infrastructure.security.AccessDenialAudit.RateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static dev.aymen.etaye.infrastructure.security.AuthorizationDenial.Reason.CAPABILITY_MISSING;
import static org.assertj.core.api.Assertions.assertThat;

class AccessDenialRateLimiterTest {

    private final MutableTestClock clock = new MutableTestClock(Instant.parse("2026-11-20T12:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void concurrentRequestsReserveOnlyOneWriteAndCooldownStartsAfterItFinishes() throws Exception {
        Key key = keyFor(0);
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> {
                start.await();
                return limiter.acquire(key);
            });
            var second = executor.submit(() -> {
                start.await();
                return limiter.acquire(key);
            });
            start.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isNotEqualTo(second.get(5, TimeUnit.SECONDS));
        }

        clock.advanceSeconds(90);
        assertThat(limiter.acquire(key)).isFalse();
        limiter.finish(key);

        clock.advanceSeconds(59);
        assertThat(limiter.acquire(key)).isFalse();
        clock.advanceSeconds(1);
        assertThat(limiter.acquire(key)).isTrue();
    }

    @Test
    void capacityRejectsNewKeysUntilCooldownsExpire() {
        for (int i = 0; i < 10_000; i++) {
            Key key = keyFor(i);
            assertThat(limiter.acquire(key)).isTrue();
            limiter.finish(key);
        }

        Key overflow = keyFor(10_000);
        assertThat(limiter.acquire(overflow)).isFalse();
        assertThat(limiter.acquire(keyFor(0))).isFalse();

        clock.advanceSeconds(60);
        assertThat(limiter.acquire(overflow)).isTrue();
        assertThat(limiter.acquire(keyFor(0))).isTrue();
    }

    private Key keyFor(long actor) {
        return new Key(new UUID(0, actor), "POST /api/v1/practices/{practiceId}/patients", CAPABILITY_MISSING);
    }
}
