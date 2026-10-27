package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.application.port.out.LeadIntakeRateLimitInvalidator;
import io.github.archetom.crm.domain.entity.LeadIntakeClient;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Local fixed-window admission control with bounded source and tenant-partitioned key buckets. */
@Component
public class LeadIntakeRateLimiter implements LeadIntakeRateLimitInvalidator {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int DEFAULT_TENANT_BUCKET_CAPACITY = 10_000;
    private static final int DEFAULT_PENDING_KEY_BUCKET_CAPACITY = 10_000;
    private static final PendingPromotionObserver NO_PENDING_PROMOTION_OBSERVER = (tenantId, keyId) -> {
    };

    private final int verifiedKeyLimit;
    private final int sourceAttemptLimit;
    private final Clock clock;
    private final SourceBucketCache sourceBuckets;
    private final TenantBucketCache verifiedKeyBuckets;
    private final PendingKeyCache pendingKeyBuckets;
    private final Object verifiedKeyAdmissionLock = new Object();
    private final PendingPromotionObserver pendingPromotionObserver;

    @Autowired
    public LeadIntakeRateLimiter(
            @Value("${atom.lead-intake.max-submissions-per-minute:60}") int verifiedKeyLimit,
            @Value("${atom.lead-intake.max-auth-attempts-per-minute:20}") int sourceAttemptLimit,
            @Value("${atom.lead-intake.source-bucket-capacity:10000}") int sourceBucketCapacity,
            @Value("${atom.lead-intake.pending-key-bucket-capacity:10000}")
            int pendingKeyBucketCapacity,
            @Value("${atom.lead-intake.verified-key-bucket-capacity-per-tenant:100}")
            int verifiedKeyBucketCapacityPerTenant,
            @Value("${atom.lead-intake.verified-key-tenant-bucket-capacity:10000}")
            int verifiedKeyTenantBucketCapacity,
            @Value("${atom.lead-intake.source-bucket-ttl-seconds:120}") long sourceBucketTtlSeconds) {
        this(verifiedKeyLimit, sourceAttemptLimit, sourceBucketCapacity, pendingKeyBucketCapacity,
                verifiedKeyBucketCapacityPerTenant, verifiedKeyTenantBucketCapacity,
                Duration.ofSeconds(sourceBucketTtlSeconds), Clock.systemUTC(),
                NO_PENDING_PROMOTION_OBSERVER);
    }

    LeadIntakeRateLimiter(
            int verifiedKeyLimit, int sourceAttemptLimit, int sourceBucketCapacity,
            Duration sourceBucketTtl, Clock clock) {
        this(verifiedKeyLimit, sourceAttemptLimit, sourceBucketCapacity,
                DEFAULT_PENDING_KEY_BUCKET_CAPACITY, LeadIntakeClient.MAX_ACTIVE_CLIENTS_PER_TENANT,
                DEFAULT_TENANT_BUCKET_CAPACITY, sourceBucketTtl, clock,
                NO_PENDING_PROMOTION_OBSERVER);
    }

    LeadIntakeRateLimiter(
            int verifiedKeyLimit, int sourceAttemptLimit, int sourceBucketCapacity,
            int verifiedKeyBucketCapacityPerTenant, int verifiedKeyTenantBucketCapacity,
            Duration sourceBucketTtl, Clock clock) {
        this(verifiedKeyLimit, sourceAttemptLimit, sourceBucketCapacity,
                DEFAULT_PENDING_KEY_BUCKET_CAPACITY,
                verifiedKeyBucketCapacityPerTenant, verifiedKeyTenantBucketCapacity,
                sourceBucketTtl, clock, NO_PENDING_PROMOTION_OBSERVER);
    }

    LeadIntakeRateLimiter(
            int verifiedKeyLimit, int sourceAttemptLimit, int sourceBucketCapacity,
            int pendingKeyBucketCapacity,
            int verifiedKeyBucketCapacityPerTenant, int verifiedKeyTenantBucketCapacity,
            Duration sourceBucketTtl, Clock clock) {
        this(verifiedKeyLimit, sourceAttemptLimit, sourceBucketCapacity,
                pendingKeyBucketCapacity, verifiedKeyBucketCapacityPerTenant,
                verifiedKeyTenantBucketCapacity, sourceBucketTtl, clock,
                NO_PENDING_PROMOTION_OBSERVER);
    }

    LeadIntakeRateLimiter(
            int verifiedKeyLimit, int sourceAttemptLimit, int sourceBucketCapacity,
            int pendingKeyBucketCapacity,
            int verifiedKeyBucketCapacityPerTenant, int verifiedKeyTenantBucketCapacity,
            Duration sourceBucketTtl, Clock clock, PendingPromotionObserver pendingPromotionObserver) {
        validateLimit(verifiedKeyLimit, "verified key");
        validateLimit(sourceAttemptLimit, "source attempt");
        validateCapacity(sourceBucketCapacity, "source bucket");
        validateCapacity(pendingKeyBucketCapacity, "pending key bucket");
        if (verifiedKeyBucketCapacityPerTenant < LeadIntakeClient.MAX_ACTIVE_CLIENTS_PER_TENANT
                || verifiedKeyBucketCapacityPerTenant > 10_000) {
            throw new IllegalArgumentException("lead intake verified key bucket capacity per tenant must be between "
                    + LeadIntakeClient.MAX_ACTIVE_CLIENTS_PER_TENANT + " and 10000");
        }
        validateCapacity(verifiedKeyTenantBucketCapacity, "verified key tenant bucket");
        if (sourceBucketTtl == null || sourceBucketTtl.toMillis() < WINDOW_MILLIS
                || sourceBucketTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("lead intake source bucket TTL must be between 60 seconds and 60 minutes");
        }
        this.verifiedKeyLimit = verifiedKeyLimit;
        this.sourceAttemptLimit = sourceAttemptLimit;
        this.clock = clock;
        this.sourceBuckets = new SourceBucketCache(sourceBucketCapacity, sourceBucketTtl.toMillis());
        this.verifiedKeyBuckets = new TenantBucketCache(verifiedKeyTenantBucketCapacity,
                verifiedKeyBucketCapacityPerTenant, sourceBucketTtl.toMillis());
        this.pendingKeyBuckets = new PendingKeyCache(pendingKeyBucketCapacity,
                sourceBucketTtl.toMillis());
        this.pendingPromotionObserver = pendingPromotionObserver == null
                ? NO_PENDING_PROMOTION_OBSERVER : pendingPromotionObserver;
    }

    /**
     * Atomically reserves one source attempt before credential lookup. A caller must settle the
     * returned attempt: successful authentication releases it, while failed authentication commits it.
     */
    public SourceAttempt tryReserveSourceAttempt(String sourceAddress) {
        String sourceKey = bucketKey(sourceAddress);
        if (!sourceBuckets.tryReserve(sourceKey, sourceAttemptLimit, clock.millis())) {
            return null;
        }
        return new SourceAttempt(sourceKey);
    }

    /**
     * Reserves an existing verified-key slot before lookup. New credential fingerprints use an
     * independent pending cache and become verified-key buckets only after secret verification.
     */
    public VerifiedKeyAttempt tryReserveVerifiedKey(
            long tenantId, String credentialFingerprint, String keyId) {
        String fingerprint = bucketKey(credentialFingerprint);
        String clientKeyId = bucketKey(keyId);
        long now = clock.millis();
        VerifiedReservation reservation = verifiedKeyBuckets.tryReserveExisting(
                tenantId, fingerprint, verifiedKeyLimit, now);
        if (reservation == VerifiedReservation.REJECTED) {
            return null;
        }
        if (reservation == VerifiedReservation.RESERVED) {
            return new VerifiedKeyAttempt(tenantId, fingerprint, clientKeyId, true);
        }
        if (!pendingKeyBuckets.tryReserve(tenantId, fingerprint, clientKeyId, now)) {
            return null;
        }
        return new VerifiedKeyAttempt(tenantId, fingerprint, clientKeyId, false);
    }

    /** Removes local admission state for a deactivated intake client without retaining its secret. */
    public void invalidateVerifiedKey(long tenantId, String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return;
        }
        synchronized (verifiedKeyAdmissionLock) {
            verifiedKeyBuckets.invalidate(tenantId, keyId);
            pendingKeyBuckets.invalidate(tenantId, keyId);
        }
    }

    @Override
    public void invalidate(TenantId tenantId, String keyId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("lead intake rate-limit invalidation requires a tenant");
        }
        invalidateVerifiedKey(tenantId.getValue(), keyId);
    }

    private static void validateLimit(int value, String description) {
        if (value < 1 || value > 10_000) {
            throw new IllegalArgumentException("lead intake " + description
                    + " rate limit must be between 1 and 10000");
        }
    }

    private static void validateCapacity(int value, String description) {
        if (value < 1 || value > 1_000_000) {
            throw new IllegalArgumentException("lead intake " + description
                    + " capacity is invalid");
        }
    }

    private static String bucketKey(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    public final class SourceAttempt {
        private final String sourceKey;
        private boolean settled;

        private SourceAttempt(String sourceKey) {
            this.sourceKey = sourceKey;
        }

        /** Releases a reservation after the credential has been verified. */
        public synchronized void succeed() {
            settle(false);
        }

        /** Converts a reservation into one failed-authentication attempt. */
        public synchronized void fail() {
            settle(true);
        }

        /** Cancels a reservation when authentication infrastructure cannot decide the outcome. */
        public synchronized void cancel() {
            settle(false);
        }

        private void settle(boolean failed) {
            if (settled) {
                return;
            }
            settled = true;
            sourceBuckets.settle(sourceKey, failed, clock.millis());
        }
    }

    public final class VerifiedKeyAttempt {
        private final long tenantId;
        private final String credentialFingerprint;
        private final String keyId;
        private final boolean existingVerifiedBucket;
        private boolean settled;

        private VerifiedKeyAttempt(
                long tenantId, String credentialFingerprint, String keyId,
                boolean existingVerifiedBucket) {
            this.tenantId = tenantId;
            this.credentialFingerprint = credentialFingerprint;
            this.keyId = keyId;
            this.existingVerifiedBucket = existingVerifiedBucket;
        }

        /** Commits a verified submission and reports whether it remains admitted. */
        public synchronized boolean succeed() {
            return settle(true);
        }

        /** Releases the reserved slot when the credential is not verified. */
        public synchronized void fail() {
            settle(false);
        }

        private boolean settle(boolean verified) {
            if (settled) {
                return false;
            }
            settled = true;
            long now = clock.millis();
            if (existingVerifiedBucket) {
                synchronized (verifiedKeyAdmissionLock) {
                    verifiedKeyBuckets.settleExisting(tenantId, credentialFingerprint, verified, now);
                    return true;
                }
            }
            synchronized (verifiedKeyAdmissionLock) {
                if (!pendingKeyBuckets.settle(tenantId, credentialFingerprint, now)) {
                    return false;
                }
                if (!verified) {
                    return true;
                }
                pendingPromotionObserver.beforeCommit(tenantId, keyId);
                return verifiedKeyBuckets.commitVerified(
                        tenantId, credentialFingerprint, keyId, verifiedKeyLimit, now);
            }
        }
    }

    private static final class SourceBucketCache {
        private final int capacity;
        private final long ttlMillis;
        private final Map<String, SourceBucket> buckets = new LinkedHashMap<>();
        private long nextExpirySweep;

        private SourceBucketCache(int capacity, long ttlMillis) {
            this.capacity = capacity;
            this.ttlMillis = ttlMillis;
        }

        private synchronized boolean tryReserve(String key, int limit, long now) {
            removeExpiredWhenDue(now);
            SourceBucket current = buckets.get(key);
            if (current == null) {
                if (!makeCapacity(now)) {
                    return false;
                }
                buckets.put(key, new SourceBucket(now, 0, 1, now));
                return true;
            }
            boolean newWindow = now - current.windowStart() >= WINDOW_MILLIS;
            long windowStart = newWindow ? now : current.windowStart();
            int failures = newWindow ? 0 : current.failures();
            if (failures + current.inFlight() >= limit) {
                return false;
            }
            buckets.put(key, new SourceBucket(windowStart, failures, current.inFlight() + 1, now));
            return true;
        }

        private synchronized void settle(String key, boolean failed, long now) {
            SourceBucket current = buckets.get(key);
            if (current == null) {
                return;
            }
            boolean newWindow = now - current.windowStart() >= WINDOW_MILLIS;
            long windowStart = newWindow ? now : current.windowStart();
            int failures = newWindow ? 0 : current.failures();
            int inFlight = Math.max(0, current.inFlight() - 1);
            if (failed) {
                failures++;
            }
            if (failures == 0 && inFlight == 0) {
                buckets.remove(key);
                return;
            }
            buckets.put(key, new SourceBucket(windowStart, failures, inFlight, now));
        }

        private boolean makeCapacity(long now) {
            if (buckets.size() < capacity) {
                return true;
            }
            removeExpired(now);
            return buckets.size() < capacity;
        }

        private void removeExpiredWhenDue(long now) {
            if (now >= nextExpirySweep) {
                removeExpired(now);
                nextExpirySweep = now + ttlMillis;
            }
        }

        private void removeExpired(long now) {
            Iterator<Map.Entry<String, SourceBucket>> iterator = buckets.entrySet().iterator();
            while (iterator.hasNext()) {
                SourceBucket bucket = iterator.next().getValue();
                if (bucket.inFlight() == 0 && now - bucket.lastSeen() >= ttlMillis) {
                    iterator.remove();
                }
            }
        }
    }

    private static final class TenantBucketCache {
        private final int tenantCapacity;
        private final int keyCapacityPerTenant;
        private final long ttlMillis;
        private final Map<Long, TenantPartition> partitions = new LinkedHashMap<>();
        private long nextExpirySweep;

        private TenantBucketCache(int tenantCapacity, int keyCapacityPerTenant, long ttlMillis) {
            this.tenantCapacity = tenantCapacity;
            this.keyCapacityPerTenant = keyCapacityPerTenant;
            this.ttlMillis = ttlMillis;
        }

        private synchronized VerifiedReservation tryReserveExisting(
                long tenantId, String credentialFingerprint, int limit, long now) {
            removeExpiredWhenDue(now);
            TenantPartition partition = partitions.get(tenantId);
            if (partition == null) {
                return VerifiedReservation.ABSENT;
            }
            VerifiedReservation reservation = partition.keyBuckets.tryReserveExisting(
                    credentialFingerprint, limit, now);
            if (reservation == VerifiedReservation.RESERVED) {
                partition.lastSeen = now;
            }
            return reservation;
        }

        private synchronized void settleExisting(
                long tenantId, String credentialFingerprint, boolean verified, long now) {
            TenantPartition partition = partitions.get(tenantId);
            if (partition == null) {
                return;
            }
            partition.keyBuckets.settleExisting(credentialFingerprint, verified, now);
            if (partition.keyBuckets.isEmpty()) {
                partitions.remove(tenantId);
                return;
            }
            partition.lastSeen = now;
        }

        private synchronized boolean commitVerified(
                long tenantId, String credentialFingerprint, String keyId, int limit, long now) {
            removeExpiredWhenDue(now);
            TenantPartition partition = partitions.get(tenantId);
            if (partition == null) {
                if (!makeTenantCapacity(now)) {
                    return false;
                }
                partition = new TenantPartition(new KeyBucketCache(keyCapacityPerTenant, ttlMillis), now);
                partitions.put(tenantId, partition);
            }
            if (!partition.keyBuckets.commitVerified(credentialFingerprint, keyId, limit, now)) {
                if (partition.keyBuckets.isEmpty()) {
                    partitions.remove(tenantId);
                }
                return false;
            }
            partition.lastSeen = now;
            return true;
        }

        private synchronized void invalidate(long tenantId, String keyId) {
            TenantPartition partition = partitions.get(tenantId);
            if (partition == null) {
                return;
            }
            partition.keyBuckets.invalidate(keyId);
            if (partition.keyBuckets.isEmpty()) {
                partitions.remove(tenantId);
            }
        }

        private boolean makeTenantCapacity(long now) {
            if (partitions.size() < tenantCapacity) {
                return true;
            }
            removeExpired(now);
            return partitions.size() < tenantCapacity;
        }

        private void removeExpiredWhenDue(long now) {
            if (now >= nextExpirySweep) {
                removeExpired(now);
                nextExpirySweep = now + ttlMillis;
            }
        }

        private void removeExpired(long now) {
            Iterator<Map.Entry<Long, TenantPartition>> iterator = partitions.entrySet().iterator();
            while (iterator.hasNext()) {
                TenantPartition partition = iterator.next().getValue();
                if (!partition.keyBuckets.hasInFlight() && now - partition.lastSeen >= ttlMillis) {
                    iterator.remove();
                }
            }
        }
    }

    private static final class KeyBucketCache {
        private final int capacity;
        private final long ttlMillis;
        private final Map<String, KeyBucket> buckets = new LinkedHashMap<>();
        private long nextExpirySweep;

        private KeyBucketCache(int capacity, long ttlMillis) {
            this.capacity = capacity;
            this.ttlMillis = ttlMillis;
        }

        private VerifiedReservation tryReserveExisting(
                String credentialFingerprint, int limit, long now) {
            removeExpiredWhenDue(now);
            KeyBucket current = buckets.get(credentialFingerprint);
            if (current == null) {
                return VerifiedReservation.ABSENT;
            }
            boolean newWindow = now - current.windowStart() >= WINDOW_MILLIS;
            long windowStart = newWindow ? now : current.windowStart();
            int successes = newWindow ? 0 : current.successes();
            if (successes + current.inFlight() >= limit) {
                return VerifiedReservation.REJECTED;
            }
            buckets.put(credentialFingerprint, new KeyBucket(windowStart, successes, current.inFlight() + 1,
                    current.keyId(), now));
            return VerifiedReservation.RESERVED;
        }

        private void settleExisting(String credentialFingerprint, boolean verified, long now) {
            KeyBucket current = buckets.get(credentialFingerprint);
            if (current == null) {
                return;
            }
            boolean newWindow = now - current.windowStart() >= WINDOW_MILLIS;
            long windowStart = newWindow ? now : current.windowStart();
            int successes = newWindow ? 0 : current.successes();
            int inFlight = Math.max(0, current.inFlight() - 1);
            if (verified) {
                successes++;
            }
            if (successes == 0 && inFlight == 0) {
                buckets.remove(credentialFingerprint);
                return;
            }
            buckets.put(credentialFingerprint, new KeyBucket(
                    windowStart, successes, inFlight, current.keyId(), now));
        }

        private boolean commitVerified(
                String credentialFingerprint, String keyId, int limit, long now) {
            KeyBucket current = buckets.get(credentialFingerprint);
            if (current == null) {
                if (!makeCapacity(now)) {
                    return false;
                }
                buckets.put(credentialFingerprint, new KeyBucket(now, 1, 0, keyId, now));
                return true;
            }
            boolean newWindow = now - current.windowStart() >= WINDOW_MILLIS;
            long windowStart = newWindow ? now : current.windowStart();
            int successes = newWindow ? 0 : current.successes();
            if (successes + current.inFlight() >= limit) {
                return false;
            }
            buckets.put(credentialFingerprint, new KeyBucket(
                    windowStart, successes + 1, current.inFlight(), current.keyId(), now));
            return true;
        }

        private void invalidate(String keyId) {
            Iterator<Map.Entry<String, KeyBucket>> iterator = buckets.entrySet().iterator();
            while (iterator.hasNext()) {
                if (keyId.equals(iterator.next().getValue().keyId())) {
                    iterator.remove();
                }
            }
        }

        private boolean makeCapacity(long now) {
            if (buckets.size() < capacity) {
                return true;
            }
            removeExpired(now);
            return buckets.size() < capacity;
        }

        private void removeExpiredWhenDue(long now) {
            if (now >= nextExpirySweep) {
                removeExpired(now);
                nextExpirySweep = now + ttlMillis;
            }
        }

        private void removeExpired(long now) {
            Iterator<Map.Entry<String, KeyBucket>> iterator = buckets.entrySet().iterator();
            while (iterator.hasNext()) {
                KeyBucket bucket = iterator.next().getValue();
                if (bucket.inFlight() == 0 && now - bucket.lastSeen >= ttlMillis) {
                    iterator.remove();
                }
            }
        }

        private boolean hasInFlight() {
            return buckets.values().stream().anyMatch(bucket -> bucket.inFlight() > 0);
        }

        private boolean isEmpty() {
            return buckets.isEmpty();
        }
    }

    /** Bounded tracking for new credential fingerprints that have not yet verified a secret. */
    private static final class PendingKeyCache {
        private final int capacity;
        private final long ttlMillis;
        private final Map<PendingKey, PendingBucket> buckets = new LinkedHashMap<>();
        private long nextExpirySweep;

        private PendingKeyCache(int capacity, long ttlMillis) {
            this.capacity = capacity;
            this.ttlMillis = ttlMillis;
        }

        private synchronized boolean tryReserve(long tenantId, String fingerprint, String keyId, long now) {
            removeExpiredWhenDue(now);
            PendingKey key = new PendingKey(tenantId, fingerprint);
            PendingBucket current = buckets.get(key);
            if (current == null) {
                if (!makeCapacity(now)) {
                    return false;
                }
                buckets.put(key, new PendingBucket(1, keyId, now));
                return true;
            }
            buckets.put(key, new PendingBucket(current.inFlight() + 1, current.keyId(), now));
            return true;
        }

        private synchronized boolean settle(long tenantId, String fingerprint, long now) {
            PendingKey key = new PendingKey(tenantId, fingerprint);
            PendingBucket current = buckets.get(key);
            if (current == null) {
                return false;
            }
            int inFlight = Math.max(0, current.inFlight() - 1);
            if (inFlight == 0) {
                buckets.remove(key);
            } else {
                buckets.put(key, new PendingBucket(inFlight, current.keyId(), now));
            }
            return true;
        }

        private synchronized void invalidate(long tenantId, String keyId) {
            Iterator<Map.Entry<PendingKey, PendingBucket>> iterator = buckets.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<PendingKey, PendingBucket> entry = iterator.next();
                if (entry.getKey().tenantId() == tenantId && keyId.equals(entry.getValue().keyId())) {
                    iterator.remove();
                }
            }
        }

        private boolean makeCapacity(long now) {
            if (buckets.size() < capacity) {
                return true;
            }
            removeExpired(now);
            return buckets.size() < capacity;
        }

        private void removeExpiredWhenDue(long now) {
            if (now >= nextExpirySweep) {
                removeExpired(now);
                nextExpirySweep = now + ttlMillis;
            }
        }

        private void removeExpired(long now) {
            Iterator<Map.Entry<PendingKey, PendingBucket>> iterator = buckets.entrySet().iterator();
            while (iterator.hasNext()) {
                PendingBucket bucket = iterator.next().getValue();
                if (bucket.inFlight() == 0 && now - bucket.lastSeen() >= ttlMillis) {
                    iterator.remove();
                }
            }
        }
    }

    private static final class TenantPartition {
        private final KeyBucketCache keyBuckets;
        private long lastSeen;

        private TenantPartition(KeyBucketCache keyBuckets, long lastSeen) {
            this.keyBuckets = keyBuckets;
            this.lastSeen = lastSeen;
        }
    }

    private record SourceBucket(long windowStart, int failures, int inFlight, long lastSeen) {
    }

    private record KeyBucket(long windowStart, int successes, int inFlight, String keyId, long lastSeen) {
    }

    private record PendingKey(long tenantId, String fingerprint) {
    }

    private record PendingBucket(int inFlight, String keyId, long lastSeen) {
    }

    private enum VerifiedReservation {
        RESERVED,
        ABSENT,
        REJECTED
    }

    @FunctionalInterface
    interface PendingPromotionObserver {
        void beforeCommit(long tenantId, String keyId);
    }
}
