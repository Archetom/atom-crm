package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.application.security.ConnectorCredential;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorAuthenticationRateLimiterTest {

    @Test
    void pendingCredentialsHaveIndependentBoundedCapacity() {
        ConnectorAuthenticationRateLimiter limiter = limiter(10, 10, 10, 1, 1);
        var first = ConnectorCredential.parse(ConnectorCredential.create().value()).orElseThrow();
        var second = ConnectorCredential.parse(ConnectorCredential.create().value()).orElseThrow();

        var reserved = limiter.tryReserveCredential(first, first.fingerprint());

        assertNotNull(reserved);
        assertNull(limiter.tryReserveCredential(second, second.fingerprint()));
        reserved.fail();
        assertNotNull(limiter.tryReserveCredential(second, second.fingerprint()));
    }

    @Test
    void verifiedCapacityRejectsPromotionWithoutEvictingALiveCredential() {
        ConnectorAuthenticationRateLimiter limiter = limiter(10, 10, 10, 2, 1);
        var first = ConnectorCredential.parse(ConnectorCredential.create().value()).orElseThrow();
        var second = ConnectorCredential.parse(ConnectorCredential.create().value()).orElseThrow();
        var firstAttempt = limiter.tryReserveCredential(first, first.fingerprint());
        var secondAttempt = limiter.tryReserveCredential(second, second.fingerprint());

        assertTrue(firstAttempt.succeed());
        assertFalse(secondAttempt.succeed());

        var existing = limiter.tryReserveCredential(first, first.fingerprint());
        assertNotNull(existing);
        assertTrue(existing.succeed());
    }

    @Test
    void verifiedMinuteQuotaAndFailedSourceQuotaFailClosed() {
        ConnectorAuthenticationRateLimiter limiter = limiter(1, 1, 10, 10, 10);
        var credential = ConnectorCredential.parse(
                ConnectorCredential.create().value()).orElseThrow();
        var firstCredential = limiter.tryReserveCredential(
                credential, credential.fingerprint());
        assertTrue(firstCredential.succeed());
        assertNull(limiter.tryReserveCredential(credential, credential.fingerprint()));

        var source = limiter.tryReserveSource("203.0.113.7");
        assertNotNull(source);
        source.fail();
        assertNull(limiter.tryReserveSource("203.0.113.7"));
    }

    @Test
    void rotationCredentialsShareOneConnectorQuota() {
        ConnectorAuthenticationRateLimiter limiter = limiter(1, 10, 10, 10, 10);
        ConnectorCredential.Generated first = ConnectorCredential.create();
        ConnectorCredential.Generated second = ConnectorCredential.rotate(first.connectorRef());
        var firstParts = ConnectorCredential.parse(first.value()).orElseThrow();
        var secondParts = ConnectorCredential.parse(second.value()).orElseThrow();

        assertTrue(limiter.tryReserveCredential(firstParts, firstParts.fingerprint()).succeed());
        var secondAttempt = limiter.tryReserveCredential(secondParts, secondParts.fingerprint());

        assertNotNull(secondAttempt);
        assertFalse(secondAttempt.succeed());
    }

    @Test
    void revokingOneKeyDoesNotResetItsConnectorQuota() {
        ConnectorAuthenticationRateLimiter limiter = limiter(1, 10, 10, 10, 10);
        ConnectorCredential.Generated first = ConnectorCredential.create();
        ConnectorCredential.Generated second = ConnectorCredential.rotate(first.connectorRef());
        var firstParts = ConnectorCredential.parse(first.value()).orElseThrow();
        var secondParts = ConnectorCredential.parse(second.value()).orElseThrow();

        assertTrue(limiter.tryReserveCredential(firstParts, firstParts.fingerprint()).succeed());
        limiter.invalidateCredential(first.connectorRef(), first.keyId());
        var secondAttempt = limiter.tryReserveCredential(secondParts, secondParts.fingerprint());

        assertNotNull(secondAttempt);
        assertFalse(secondAttempt.succeed());
    }

    @Test
    void differentConnectorsHaveIndependentMinuteQuotas() {
        ConnectorAuthenticationRateLimiter limiter = limiter(1, 10, 10, 10, 10);
        var first = ConnectorCredential.parse(ConnectorCredential.create().value()).orElseThrow();
        var second = ConnectorCredential.parse(ConnectorCredential.create().value()).orElseThrow();

        assertTrue(limiter.tryReserveCredential(first, first.fingerprint()).succeed());
        assertTrue(limiter.tryReserveCredential(second, second.fingerprint()).succeed());
    }

    private ConnectorAuthenticationRateLimiter limiter(
            int connectorLimit, int sourceLimit, int sourceCapacity,
            int pendingCapacity, int verifiedCapacity) {
        return new ConnectorAuthenticationRateLimiter(
                connectorLimit, sourceLimit, sourceCapacity, pendingCapacity, verifiedCapacity,
                Duration.ofMinutes(2), Clock.systemUTC());
    }
}
