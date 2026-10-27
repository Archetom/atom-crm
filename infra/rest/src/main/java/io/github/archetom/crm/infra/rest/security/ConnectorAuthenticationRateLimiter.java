package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.application.port.out.ConnectorAuthenticationStateInvalidator;
import io.github.archetom.crm.application.security.ConnectorCredential;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded local admission control. Unknown credentials occupy only the global pending cache;
 * connector-partitioned buckets are created after successful secret verification.
 */
@Component
public class ConnectorAuthenticationRateLimiter
        implements ConnectorAuthenticationStateInvalidator {
    private static final long WINDOW_MILLIS = 60_000L;

    private final int connectorLimit;
    private final int sourceFailureLimit;
    private final int sourceCapacity;
    private final int pendingCapacity;
    private final int verifiedCapacity;
    private final long ttlMillis;
    private final Clock clock;
    private final Map<String, SourceBucket> sources = new LinkedHashMap<>();
    private final Map<String, PendingBucket> pending = new LinkedHashMap<>();
    /** Verified partitions are keyed by connectorRef; credential fingerprints remain children. */
    private final Map<String, VerifiedBucket> verified = new LinkedHashMap<>();

    @Autowired
    public ConnectorAuthenticationRateLimiter(
            @Value("${atom.connector-ingress.max-requests-per-minute:600}") int connectorLimit,
            @Value("${atom.connector-ingress.max-auth-failures-per-minute:20}") int sourceFailureLimit,
            @Value("${atom.connector-ingress.source-bucket-capacity:10000}") int sourceCapacity,
            @Value("${atom.connector-ingress.pending-credential-capacity:10000}") int pendingCapacity,
            @Value("${atom.connector-ingress.verified-credential-capacity:10000}") int verifiedCapacity,
            @Value("${atom.connector-ingress.bucket-ttl-seconds:120}") long ttlSeconds) {
        this(connectorLimit, sourceFailureLimit, sourceCapacity, pendingCapacity,
                verifiedCapacity, Duration.ofSeconds(ttlSeconds), Clock.systemUTC());
    }

    ConnectorAuthenticationRateLimiter(
            int connectorLimit, int sourceFailureLimit, int sourceCapacity,
            int pendingCapacity, int verifiedCapacity, Duration ttl, Clock clock) {
        requireRange(connectorLimit, 1, 100_000, "connector request limit");
        requireRange(sourceFailureLimit, 1, 10_000, "source failure limit");
        requireRange(sourceCapacity, 1, 1_000_000, "source capacity");
        requireRange(pendingCapacity, 1, 1_000_000, "pending capacity");
        requireRange(verifiedCapacity, 1, 1_000_000, "verified capacity");
        if (ttl == null || ttl.toMillis() < WINDOW_MILLIS
                || ttl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException(
                    "connector authentication bucket TTL must be between 60 seconds and 60 minutes");
        }
        this.connectorLimit = connectorLimit;
        this.sourceFailureLimit = sourceFailureLimit;
        this.sourceCapacity = sourceCapacity;
        this.pendingCapacity = pendingCapacity;
        this.verifiedCapacity = verifiedCapacity;
        this.ttlMillis = ttl.toMillis();
        this.clock = clock;
    }

    public synchronized SourceAttempt tryReserveSource(String source) {
        long now = clock.millis();
        cleanup(now);
        String key = source == null || source.isBlank() ? "unknown" : source;
        SourceBucket bucket = sources.get(key);
        if (bucket == null) {
            if (sources.size() >= sourceCapacity) {
                return null;
            }
            bucket = new SourceBucket(now, 0, 0, now);
            sources.put(key, bucket);
        }
        resetSourceWindow(bucket, now);
        if (bucket.failures + bucket.inFlight >= sourceFailureLimit) {
            return null;
        }
        bucket.inFlight++;
        bucket.lastSeen = now;
        return new SourceAttempt(key);
    }

    public synchronized CredentialAttempt tryReserveCredential(
            ConnectorCredential.Parts parts, String fingerprint) {
        if (parts == null || fingerprint == null || fingerprint.isBlank()) {
            return null;
        }
        long now = clock.millis();
        cleanup(now);
        String connectorRef = parts.connectorRef().getValue();
        VerifiedBucket known = verified.get(connectorRef);
        if (known != null && known.matches(parts, fingerprint)) {
            resetVerifiedWindow(known, now);
            if (known.accepted + known.inFlight >= connectorLimit) {
                return null;
            }
            known.inFlight++;
            known.lastSeen = now;
            return new CredentialAttempt(connectorRef, fingerprint, true);
        }
        PendingBucket candidate = pending.get(fingerprint);
        if (candidate == null) {
            if (pending.size() >= pendingCapacity) {
                return null;
            }
            candidate = new PendingBucket(parts.connectorRef().getValue(), parts.keyId(), 0, now);
            pending.put(fingerprint, candidate);
        }
        if (!candidate.matches(parts) || candidate.inFlight >= connectorLimit) {
            return null;
        }
        candidate.inFlight++;
        candidate.lastSeen = now;
        return new CredentialAttempt(connectorRef, fingerprint, false);
    }

    @Override
    public synchronized void invalidateCredential(ConnectorRef connectorRef, String keyId) {
        if (connectorRef == null || keyId == null) {
            return;
        }
        VerifiedBucket bucket = verified.get(connectorRef.getValue());
        if (bucket != null) {
            bucket.removeCredential(keyId);
            bucket.lastSeen = clock.millis();
        }
        pending.entrySet().removeIf(entry -> entry.getValue().connectorRef
                .equals(connectorRef.getValue()) && entry.getValue().keyId.equals(keyId));
    }

    @Override
    public synchronized void invalidateConnector(ConnectorRef connectorRef) {
        if (connectorRef == null) {
            return;
        }
        verified.remove(connectorRef.getValue());
        pending.entrySet().removeIf(entry ->
                entry.getValue().connectorRef.equals(connectorRef.getValue()));
    }

    public final class SourceAttempt {
        private final String key;
        private boolean settled;

        private SourceAttempt(String key) {
            this.key = key;
        }

        public void succeed() {
            settle(false);
        }

        public void fail() {
            settle(true);
        }

        public void cancel() {
            settle(false);
        }

        private synchronized void settle(boolean failed) {
            if (settled) {
                return;
            }
            settled = true;
            settleSource(key, failed);
        }
    }

    public final class CredentialAttempt {
        private final String connectorRef;
        private final String fingerprint;
        private final boolean previouslyVerified;
        private boolean settled;

        private CredentialAttempt(
                String connectorRef, String fingerprint, boolean previouslyVerified) {
            this.connectorRef = connectorRef;
            this.fingerprint = fingerprint;
            this.previouslyVerified = previouslyVerified;
        }

        public synchronized boolean succeed() {
            if (settled) {
                return false;
            }
            settled = true;
            return settleCredential(connectorRef, fingerprint, previouslyVerified, true);
        }

        public synchronized void fail() {
            if (!settled) {
                settled = true;
                settleCredential(connectorRef, fingerprint, previouslyVerified, false);
            }
        }
    }

    private synchronized void settleSource(String key, boolean failed) {
        long now = clock.millis();
        SourceBucket bucket = sources.get(key);
        if (bucket == null) {
            return;
        }
        resetSourceWindow(bucket, now);
        bucket.inFlight = Math.max(0, bucket.inFlight - 1);
        if (failed) {
            bucket.failures++;
        }
        bucket.lastSeen = now;
        if (bucket.failures == 0 && bucket.inFlight == 0) {
            sources.remove(key);
        }
    }

    private synchronized boolean settleCredential(
            String connectorRef, String fingerprint,
            boolean previouslyVerified, boolean accepted) {
        long now = clock.millis();
        if (previouslyVerified) {
            VerifiedBucket bucket = verified.get(connectorRef);
            if (bucket == null) {
                return accepted;
            }
            resetVerifiedWindow(bucket, now);
            bucket.inFlight = Math.max(0, bucket.inFlight - 1);
            if (accepted) {
                if (bucket.accepted >= connectorLimit) {
                    return false;
                }
                bucket.accepted++;
            }
            bucket.lastSeen = now;
            return true;
        }
        PendingBucket candidate = pending.get(fingerprint);
        if (candidate == null) {
            return accepted;
        }
        candidate.inFlight = Math.max(0, candidate.inFlight - 1);
        candidate.lastSeen = now;
        if (!accepted) {
            if (candidate.inFlight == 0) {
                pending.remove(fingerprint);
            }
            return true;
        }
        if (!candidate.connectorRef.equals(connectorRef)) {
            if (candidate.inFlight == 0) {
                pending.remove(fingerprint);
            }
            return false;
        }
        VerifiedBucket bucket = verified.get(connectorRef);
        if (bucket == null) {
            if (verified.size() >= verifiedCapacity) {
                if (candidate.inFlight == 0) {
                    pending.remove(fingerprint);
                }
                return false;
            }
            bucket = new VerifiedBucket(candidate.connectorRef, now, 0, 0, now);
            verified.put(connectorRef, bucket);
        }
        bucket.addCredential(fingerprint, candidate.keyId);
        resetVerifiedWindow(bucket, now);
        boolean admitted = true;
        if (bucket.accepted >= connectorLimit) {
            admitted = false;
        } else {
            bucket.accepted++;
        }
        bucket.lastSeen = now;
        if (candidate.inFlight == 0) {
            pending.remove(fingerprint);
        }
        return admitted;
    }

    private void cleanup(long now) {
        removeExpired(sources.entrySet().iterator(), now,
                entry -> entry.getValue().inFlight == 0 && now - entry.getValue().lastSeen >= ttlMillis);
        removeExpired(pending.entrySet().iterator(), now,
                entry -> entry.getValue().inFlight == 0 && now - entry.getValue().lastSeen >= ttlMillis);
        removeExpired(verified.entrySet().iterator(), now,
                entry -> entry.getValue().inFlight == 0 && now - entry.getValue().lastSeen >= ttlMillis);
    }

    private <T> void removeExpired(
            Iterator<Map.Entry<String, T>> iterator, long now,
            java.util.function.Predicate<Map.Entry<String, T>> predicate) {
        while (iterator.hasNext()) {
            if (predicate.test(iterator.next())) {
                iterator.remove();
            }
        }
    }

    private void resetSourceWindow(SourceBucket bucket, long now) {
        if (now - bucket.windowStart >= WINDOW_MILLIS) {
            bucket.windowStart = now;
            bucket.failures = 0;
        }
    }

    private void resetVerifiedWindow(VerifiedBucket bucket, long now) {
        if (now - bucket.windowStart >= WINDOW_MILLIS) {
            bucket.windowStart = now;
            bucket.accepted = 0;
        }
    }

    private static void requireRange(int value, int min, int max, String description) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("connector authentication " + description + " is invalid");
        }
    }

    private static final class SourceBucket {
        private long windowStart;
        private int failures;
        private int inFlight;
        private long lastSeen;

        private SourceBucket(long windowStart, int failures, int inFlight, long lastSeen) {
            this.windowStart = windowStart;
            this.failures = failures;
            this.inFlight = inFlight;
            this.lastSeen = lastSeen;
        }
    }

    private static final class PendingBucket {
        private final String connectorRef;
        private final String keyId;
        private int inFlight;
        private long lastSeen;

        private PendingBucket(String connectorRef, String keyId, int inFlight, long lastSeen) {
            this.connectorRef = connectorRef;
            this.keyId = keyId;
            this.inFlight = inFlight;
            this.lastSeen = lastSeen;
        }

        private boolean matches(ConnectorCredential.Parts parts) {
            return connectorRef.equals(parts.connectorRef().getValue()) && keyId.equals(parts.keyId());
        }
    }

    private static final class VerifiedBucket {
        private final String connectorRef;
        private final Map<String, String> credentials = new LinkedHashMap<>();
        private long windowStart;
        private int accepted;
        private int inFlight;
        private long lastSeen;

        private VerifiedBucket(String connectorRef, long windowStart,
                               int accepted, int inFlight, long lastSeen) {
            this.connectorRef = connectorRef;
            this.windowStart = windowStart;
            this.accepted = accepted;
            this.inFlight = inFlight;
            this.lastSeen = lastSeen;
        }

        private boolean matches(ConnectorCredential.Parts parts, String fingerprint) {
            return connectorRef.equals(parts.connectorRef().getValue())
                    && parts.keyId().equals(credentials.get(fingerprint));
        }

        private void addCredential(String fingerprint, String keyId) {
            credentials.put(fingerprint, keyId);
        }

        private void removeCredential(String keyId) {
            credentials.entrySet().removeIf(entry -> entry.getValue().equals(keyId));
        }
    }
}
