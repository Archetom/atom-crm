package io.github.archetom.crm.infra.rest.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeadIntakeRateLimiterTest {

    @Test
    void sourceBucketsExpireAndTheirCapacityRejectsNewSourcesUntilThen() {
        MutableClock clock = new MutableClock();
        LeadIntakeRateLimiter limiter = new LeadIntakeRateLimiter(
                60, 2, 2, Duration.ofMinutes(2), clock);

        failedAttempt(limiter, "198.51.100.1");
        failedAttempt(limiter, "198.51.100.2");
        assertNull(limiter.tryReserveSourceAttempt("198.51.100.3"));

        clock.advance(Duration.ofMinutes(2));
        assertNotNull(limiter.tryReserveSourceAttempt("198.51.100.3"));
    }

    @Test
    void onlyFailedAuthenticationAttemptsConsumeTheSourceLimit() {
        MutableClock clock = new MutableClock();
        LeadIntakeRateLimiter limiter = new LeadIntakeRateLimiter(
                2, 1, 2, Duration.ofMinutes(2), clock);

        failedAttempt(limiter, "198.51.100.1");
        assertNull(limiter.tryReserveSourceAttempt("198.51.100.1"));
        verifiedAttempt(limiter, 9L, "client-key");
        verifiedAttempt(limiter, 9L, "client-key");
        assertNull(limiter.tryReserveVerifiedKey(9L, "client-key", "client-key"));
    }

    @Test
    void successfulRequestsCanUseTheEntireVerifiedKeyLimitFromOneSource() {
        MutableClock clock = new MutableClock();
        LeadIntakeRateLimiter limiter = new LeadIntakeRateLimiter(
                60, 1, 2, Duration.ofMinutes(2), clock);

        for (int request = 0; request < 60; request++) {
            LeadIntakeRateLimiter.SourceAttempt attempt = limiter
                    .tryReserveSourceAttempt("198.51.100.1");
            assertNotNull(attempt);
            attempt.succeed();
            verifiedAttempt(limiter, 9L, "client-key");
        }
        assertNull(limiter.tryReserveVerifiedKey(9L, "client-key", "client-key"));
    }

    @Test
    void fullTenantPartitionDoesNotResetAnExistingTenantsQuota() {
        MutableClock clock = new MutableClock();
        LeadIntakeRateLimiter limiter = new LeadIntakeRateLimiter(
                1, 1, 2, 100, 1, Duration.ofMinutes(2), clock);

        verifiedAttempt(limiter, 9L, "tenant-a-key");
        LeadIntakeRateLimiter.VerifiedKeyAttempt otherTenant = limiter.tryReserveVerifiedKey(
                10L, "tenant-b-key", "tenant-b-key");
        assertNotNull(otherTenant);
        assertFalse(otherTenant.succeed());
        assertNull(limiter.tryReserveVerifiedKey(9L, "tenant-a-key", "tenant-a-key"));
    }

    @Test
    void invalidatingADeactivatedKeyFreesItsTenantBucketSlotForRotation() {
        MutableClock clock = new MutableClock();
        LeadIntakeRateLimiter limiter = new LeadIntakeRateLimiter(
                60, 1, 2, 100, 2, Duration.ofMinutes(2), clock);

        for (int key = 0; key < 100; key++) {
            verifiedAttempt(limiter, 9L, "fingerprint-" + key, "key-" + key);
        }
        LeadIntakeRateLimiter.VerifiedKeyAttempt beforeDeactivation = limiter.tryReserveVerifiedKey(
                9L, "replacement-fingerprint", "replacement-key");
        assertNotNull(beforeDeactivation);
        assertFalse(beforeDeactivation.succeed());

        limiter.invalidateVerifiedKey(9L, "key-0");
        LeadIntakeRateLimiter.VerifiedKeyAttempt replacement = limiter.tryReserveVerifiedKey(
                9L, "replacement-fingerprint", "replacement-key");
        assertNotNull(replacement);
        assertTrue(replacement.succeed());
    }

    @Test
    void fakePendingCredentialsCannotConsumeVerifiedKeyBucketCapacity() {
        MutableClock clock = new MutableClock();
        LeadIntakeRateLimiter limiter = new LeadIntakeRateLimiter(
                60, 20, 200, 200, 100, 2, Duration.ofMinutes(2), clock);

        List<LeadIntakeRateLimiter.VerifiedKeyAttempt> fakeReservations = IntStream.range(0, 100)
                .parallel()
                .mapToObj(key -> limiter.tryReserveVerifiedKey(
                        9L, "fake-fingerprint-" + key, "fake-key-" + key))
                .toList();
        fakeReservations.forEach(value -> assertNotNull(value));

        LeadIntakeRateLimiter.VerifiedKeyAttempt legitimate = limiter.tryReserveVerifiedKey(
                9L, "legitimate-fingerprint", "legitimate-key");
        assertNotNull(legitimate);
        assertTrue(legitimate.succeed());
    }

    @Test
    void invalidatingAnInFlightKeyReservationCannotResurrectItsBucketAfterVerification() {
        MutableClock clock = new MutableClock();
        LeadIntakeRateLimiter limiter = new LeadIntakeRateLimiter(
                60, 1, 2, 100, 2, Duration.ofMinutes(2), clock);
        for (int key = 0; key < 99; key++) {
            verifiedAttempt(limiter, 9L, "fingerprint-" + key, "key-" + key);
        }
        LeadIntakeRateLimiter.VerifiedKeyAttempt inFlight = limiter.tryReserveVerifiedKey(
                9L, "in-flight-fingerprint", "old-key");
        assertNotNull(inFlight);

        limiter.invalidateVerifiedKey(9L, "old-key");
        assertFalse(inFlight.succeed());

        assertNotNull(limiter.tryReserveVerifiedKey(
                9L, "replacement-fingerprint", "replacement-key"));
    }

    @Test
    void invalidationAndPendingPromotionAreAtomicDuringKeyRotation() throws Exception {
        MutableClock clock = new MutableClock();
        CountDownLatch pendingRemoved = new CountDownLatch(1);
        CountDownLatch continuePromotion = new CountDownLatch(1);
        LeadIntakeRateLimiter limiter = new LeadIntakeRateLimiter(
                60, 1, 2, 100, 100, 2, Duration.ofMinutes(2), clock, (tenantId, keyId) -> {
                    if (!"old-key".equals(keyId)) {
                        return;
                    }
                    pendingRemoved.countDown();
                    await(continuePromotion);
                });
        for (int key = 0; key < 99; key++) {
            verifiedAttempt(limiter, 9L, "fingerprint-" + key, "key-" + key);
        }
        LeadIntakeRateLimiter.VerifiedKeyAttempt oldKey = limiter.tryReserveVerifiedKey(
                9L, "old-fingerprint", "old-key");
        assertNotNull(oldKey);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> promotion = executor.submit(oldKey::succeed);
            assertTrue(pendingRemoved.await(5, TimeUnit.SECONDS));
            Thread invalidation = new Thread(
                    () -> limiter.invalidateVerifiedKey(9L, "old-key"), "lead-intake-invalidation");
            invalidation.start();
            awaitBlocked(invalidation);

            continuePromotion.countDown();
            assertTrue(promotion.get(5, TimeUnit.SECONDS));
            invalidation.join(TimeUnit.SECONDS.toMillis(5));
            assertFalse(invalidation.isAlive());

            LeadIntakeRateLimiter.VerifiedKeyAttempt replacement = limiter.tryReserveVerifiedKey(
                    9L, "replacement-fingerprint", "replacement-key");
            assertNotNull(replacement);
            assertTrue(replacement.succeed());
        } finally {
            continuePromotion.countDown();
            executor.shutdownNow();
        }
    }

    private void failedAttempt(LeadIntakeRateLimiter limiter, String sourceAddress) {
        LeadIntakeRateLimiter.SourceAttempt attempt = limiter.tryReserveSourceAttempt(sourceAddress);
        assertNotNull(attempt);
        attempt.fail();
    }

    private void verifiedAttempt(LeadIntakeRateLimiter limiter, long tenantId, String fingerprint) {
        verifiedAttempt(limiter, tenantId, fingerprint, fingerprint);
    }

    private void verifiedAttempt(
            LeadIntakeRateLimiter limiter, long tenantId, String fingerprint, String keyId) {
        LeadIntakeRateLimiter.VerifiedKeyAttempt attempt = limiter
                .tryReserveVerifiedKey(tenantId, fingerprint, keyId);
        assertNotNull(attempt);
        assertTrue(attempt.succeed());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for promotion to continue");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Promotion was interrupted", exception);
        }
    }

    private static void awaitBlocked(Thread thread) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED) {
            if (System.nanoTime() >= deadlineNanos) {
                throw new AssertionError("Invalidation thread did not block on promotion coordination");
            }
            Thread.sleep(1);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-07-16T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
