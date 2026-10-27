package io.github.archetom.crm.infra.rest.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationRateLimiterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    private AuthenticationRateLimiter limiter() {
        return new AuthenticationRateLimiter(3, 2, 4, 100, Duration.ofSeconds(120), clock);
    }

    @Test
    void credentialLaneRefusesOnceTheWindowIsSpent() {
        AuthenticationRateLimiter limiter = limiter();
        for (int attempt = 0; attempt < 3; attempt++) {
            assertTrue(limiter.tryAcquire(AuthenticationRateLimiter.Lane.CREDENTIAL, "203.0.113.7"));
        }
        assertFalse(limiter.tryAcquire(AuthenticationRateLimiter.Lane.CREDENTIAL, "203.0.113.7"));
    }

    @Test
    void windowReopensAfterOneMinute() {
        AuthenticationRateLimiter limiter = limiter();
        for (int attempt = 0; attempt < 3; attempt++) {
            limiter.tryAcquire(AuthenticationRateLimiter.Lane.CREDENTIAL, "203.0.113.7");
        }
        now.set(now.get().plusSeconds(61));
        assertTrue(limiter.tryAcquire(AuthenticationRateLimiter.Lane.CREDENTIAL, "203.0.113.7"));
    }

    @Test
    void sourcesAreMeteredIndependently() {
        AuthenticationRateLimiter limiter = limiter();
        for (int attempt = 0; attempt < 3; attempt++) {
            limiter.tryAcquire(AuthenticationRateLimiter.Lane.CREDENTIAL, "203.0.113.7");
        }
        assertFalse(limiter.tryAcquire(AuthenticationRateLimiter.Lane.CREDENTIAL, "203.0.113.7"));
        assertTrue(limiter.tryAcquire(AuthenticationRateLimiter.Lane.CREDENTIAL, "203.0.113.8"));
    }

    @Test
    void exhaustingOneLaneLeavesTheOthersOpen() {
        AuthenticationRateLimiter limiter = limiter();
        for (int attempt = 0; attempt < 2; attempt++) {
            limiter.tryAcquire(AuthenticationRateLimiter.Lane.RESET_REQUEST, "203.0.113.7");
        }
        assertFalse(limiter.tryAcquire(AuthenticationRateLimiter.Lane.RESET_REQUEST, "203.0.113.7"));
        assertTrue(limiter.tryAcquire(AuthenticationRateLimiter.Lane.CREDENTIAL, "203.0.113.7"));
        assertTrue(limiter.tryAcquire(AuthenticationRateLimiter.Lane.STATUS, "203.0.113.7"));
    }

    @Test
    void aFullSourceCacheRefusesRatherThanGrowingWithoutBound() {
        AuthenticationRateLimiter limiter =
                new AuthenticationRateLimiter(10, 10, 10, 2, Duration.ofSeconds(120), clock);
        assertTrue(limiter.tryAcquire(AuthenticationRateLimiter.Lane.CREDENTIAL, "203.0.113.1"));
        assertTrue(limiter.tryAcquire(AuthenticationRateLimiter.Lane.CREDENTIAL, "203.0.113.2"));
        assertFalse(limiter.tryAcquire(AuthenticationRateLimiter.Lane.CREDENTIAL, "203.0.113.3"));
    }

    @Test
    void missingSourceStillConsumesABoundedSharedSlot() {
        AuthenticationRateLimiter limiter = limiter();
        for (int attempt = 0; attempt < 3; attempt++) {
            assertTrue(limiter.tryAcquire(AuthenticationRateLimiter.Lane.CREDENTIAL, null));
        }
        assertFalse(limiter.tryAcquire(AuthenticationRateLimiter.Lane.CREDENTIAL, "   "));
    }

    @Test
    void invalidConfigurationIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () ->
                new AuthenticationRateLimiter(0, 5, 5, 100, Duration.ofSeconds(120), clock));
        assertThrows(IllegalArgumentException.class, () ->
                new AuthenticationRateLimiter(5, 5, 5, 100, Duration.ofSeconds(10), clock));
        assertThrows(IllegalArgumentException.class, () ->
                new AuthenticationRateLimiter(5, 5, 5, 0, Duration.ofSeconds(120), clock));
    }
}
