package io.github.archetom.crm.infra.rest.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded local fixed-window admission control for the anonymous authentication and installation
 * routes.
 *
 * <p>These are the only public entry points that spend password-hashing CPU or send mail, and they
 * are reachable without any credential. Each lane keeps its own bounded cache so that flooding one
 * route cannot evict another's quota. This is admission control, not a source of authorization
 * truth: it bounds cost per source within one replica and never decides who a caller is.</p>
 */
@Component
public class AuthenticationRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final int credentialLimit;
    private final int resetRequestLimit;
    private final int statusLimit;
    private final long bucketTtlMillis;
    private final Clock clock;
    private final Map<Lane, BucketCache> lanes = new LinkedHashMap<>();

    @Autowired
    public AuthenticationRateLimiter(
            @Value("${atom.authentication.max-credential-attempts-per-source-per-minute:20}")
            int credentialLimit,
            @Value("${atom.authentication.max-reset-requests-per-source-per-minute:5}")
            int resetRequestLimit,
            @Value("${atom.authentication.max-status-reads-per-source-per-minute:30}")
            int statusLimit,
            @Value("${atom.authentication.source-bucket-capacity:10000}") int sourceBucketCapacity,
            @Value("${atom.authentication.bucket-ttl-seconds:120}") long bucketTtlSeconds) {
        this(credentialLimit, resetRequestLimit, statusLimit, sourceBucketCapacity,
                Duration.ofSeconds(bucketTtlSeconds), Clock.systemUTC());
    }

    AuthenticationRateLimiter(
            int credentialLimit, int resetRequestLimit, int statusLimit,
            int sourceBucketCapacity, Duration bucketTtl, Clock clock) {
        validateLimit(credentialLimit, "credential");
        validateLimit(resetRequestLimit, "reset request");
        validateLimit(statusLimit, "status");
        if (sourceBucketCapacity < 1 || sourceBucketCapacity > 1_000_000) {
            throw new IllegalArgumentException("authentication source bucket capacity is invalid");
        }
        if (bucketTtl == null || bucketTtl.toMillis() < WINDOW_MILLIS
                || bucketTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException(
                    "authentication bucket TTL must be between 60 seconds and 60 minutes");
        }
        this.credentialLimit = credentialLimit;
        this.resetRequestLimit = resetRequestLimit;
        this.statusLimit = statusLimit;
        this.bucketTtlMillis = bucketTtl.toMillis();
        this.clock = clock == null ? Clock.systemUTC() : clock;
        for (Lane lane : Lane.values()) {
            lanes.put(lane, new BucketCache(sourceBucketCapacity));
        }
    }

    /** Consumes one slot for the resolved source, or refuses when the lane's window is spent. */
    public synchronized boolean tryAcquire(Lane lane, String sourceAddress) {
        if (lane == null) {
            return true;
        }
        long now = clock.millis();
        BucketCache cache = lanes.get(lane);
        cache.removeExpired(now, bucketTtlMillis);

        String key = sourceAddress == null || sourceAddress.isBlank() ? "unknown" : sourceAddress;
        int limit = limitFor(lane);
        Bucket bucket = cache.get(key);
        if (bucket == null && cache.isFull()) {
            // A full cache means the replica is already saturated; admitting an unbounded
            // number of new sources would defeat the ceiling this lane exists to hold.
            return false;
        }
        if (bucket != null && !bucket.allows(limit, now)) {
            return false;
        }
        cache.getOrCreate(key, now).acquire(now);
        return true;
    }

    private int limitFor(Lane lane) {
        return switch (lane) {
            case CREDENTIAL -> credentialLimit;
            case RESET_REQUEST -> resetRequestLimit;
            case STATUS -> statusLimit;
        };
    }

    /** Anonymous route families, separated by what an abusive caller actually consumes. */
    public enum Lane {
        /** Password verification, session creation, reset confirmation, initial-owner creation. */
        CREDENTIAL,
        /** Reset mail delivery, where the cost lands on a third party's mailbox. */
        RESET_REQUEST,
        /** Installation availability, which is cheap but still a publicly readable state bit. */
        STATUS
    }

    private static void validateLimit(int value, String description) {
        if (value < 1 || value > 10_000) {
            throw new IllegalArgumentException("authentication " + description
                    + " rate limit must be between 1 and 10000");
        }
    }

    private static final class BucketCache {
        private final int capacity;
        private final Map<String, Bucket> buckets = new LinkedHashMap<>();

        private BucketCache(int capacity) {
            this.capacity = capacity;
        }

        private Bucket get(String key) {
            return buckets.get(key);
        }

        private boolean isFull() {
            return buckets.size() >= capacity;
        }

        private Bucket getOrCreate(String key, long now) {
            return buckets.computeIfAbsent(key, ignored -> new Bucket(now));
        }

        private void removeExpired(long now, long ttlMillis) {
            Iterator<Bucket> iterator = buckets.values().iterator();
            while (iterator.hasNext()) {
                if (now - iterator.next().lastActivity >= ttlMillis) {
                    iterator.remove();
                }
            }
        }
    }

    private static final class Bucket {
        private long windowStart;
        private long lastActivity;
        private int count;

        private Bucket(long now) {
            windowStart = now;
            lastActivity = now;
        }

        private boolean allows(int limit, long now) {
            return now - windowStart >= WINDOW_MILLIS || count < limit;
        }

        private void acquire(long now) {
            if (now - windowStart >= WINDOW_MILLIS) {
                windowStart = now;
                count = 0;
            }
            count++;
            lastActivity = now;
        }
    }
}
