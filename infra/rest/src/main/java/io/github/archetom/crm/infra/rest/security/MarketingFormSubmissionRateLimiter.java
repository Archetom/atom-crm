package io.github.archetom.crm.infra.rest.security;

import io.github.archetom.crm.application.properties.PublicMarketingFileProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded local fixed-window admission control for anonymous marketing-form submissions. */
@Component
public class MarketingFormSubmissionRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int PUBLIC_FILE_FALLBACK_FORM_LIMIT = 120;
    private static final int PUBLIC_FILE_FALLBACK_SOURCE_LIMIT = 30;

    private final int formLimit;
    private final int sourceLimit;
    private final int publicFileFormLimit;
    private final int publicFileSourceLimit;
    private final boolean publicFileLimitsValid;
    private final long bucketTtlMillis;
    private final Clock clock;
    private final BucketCache submissionFormBuckets;
    private final BucketCache submissionSourceBuckets;
    private final BucketCache publicFileFormBuckets;
    private final BucketCache publicFileSourceBuckets;

    @Autowired
    public MarketingFormSubmissionRateLimiter(
            @Value("${atom.marketing-form.max-submissions-per-minute:60}") int formLimit,
            @Value("${atom.marketing-form.max-submissions-per-source-per-minute:20}")
            int sourceLimit,
            @Value("${atom.files.public-marketing.max-requests-per-form-per-minute:120}")
            int publicFileFormLimit,
            @Value("${atom.files.public-marketing.max-requests-per-source-per-minute:30}")
            int publicFileSourceLimit,
            @Value("${atom.marketing-form.form-bucket-capacity:10000}")
            int formBucketCapacity,
            @Value("${atom.marketing-form.source-bucket-capacity:10000}")
            int sourceBucketCapacity,
            @Value("${atom.marketing-form.bucket-ttl-seconds:120}") long bucketTtlSeconds) {
        this(formLimit, sourceLimit, publicFileFormLimit, publicFileSourceLimit,
                formBucketCapacity, sourceBucketCapacity,
                Duration.ofSeconds(bucketTtlSeconds), Clock.systemUTC());
    }

    MarketingFormSubmissionRateLimiter(
            int formLimit, int sourceLimit, int formBucketCapacity,
            int sourceBucketCapacity, Duration bucketTtl, Clock clock) {
        this(formLimit, sourceLimit, 120, 30, formBucketCapacity, sourceBucketCapacity, bucketTtl, clock);
    }

    MarketingFormSubmissionRateLimiter(
            int formLimit, int sourceLimit, int publicFileFormLimit, int publicFileSourceLimit,
            int formBucketCapacity, int sourceBucketCapacity, Duration bucketTtl, Clock clock) {
        validateLimit(formLimit, "form");
        validateLimit(sourceLimit, "source");
        validateCapacity(formBucketCapacity, "form");
        validateCapacity(sourceBucketCapacity, "source");
        if (bucketTtl == null || bucketTtl.toMillis() < WINDOW_MILLIS
                || bucketTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException(
                    "marketing form bucket TTL must be between 60 seconds and 60 minutes");
        }
        this.formLimit = formLimit;
        this.sourceLimit = sourceLimit;
        // Anonymous FILE is an opt-in lane. Invalid values must close that lane, not prevent
        // scalar public forms or all internal attachment features from starting.
        this.publicFileLimitsValid = PublicMarketingFileProperties.validRequestLimits(
                publicFileFormLimit, publicFileSourceLimit);
        this.publicFileFormLimit = publicFileLimitsValid ? publicFileFormLimit
                : PUBLIC_FILE_FALLBACK_FORM_LIMIT;
        this.publicFileSourceLimit = publicFileLimitsValid ? publicFileSourceLimit
                : PUBLIC_FILE_FALLBACK_SOURCE_LIMIT;
        this.bucketTtlMillis = bucketTtl.toMillis();
        this.clock = clock == null ? Clock.systemUTC() : clock;
        // Public FILE traffic has a fully isolated bounded cache. It cannot use its route
        // keys to evict or fill the scalar MarketingForm admission lane.
        this.submissionFormBuckets = new BucketCache(formBucketCapacity);
        this.submissionSourceBuckets = new BucketCache(sourceBucketCapacity);
        this.publicFileFormBuckets = new BucketCache(formBucketCapacity);
        this.publicFileSourceBuckets = new BucketCache(sourceBucketCapacity);
    }

    /** Atomically consumes both the per-form and resolved-source quota before request handling. */
    public synchronized boolean tryAcquire(String publicId, String sourceAddress) {
        return tryAcquire(publicId, sourceAddress, RouteKind.SUBMISSION);
    }

    /** Route lanes have independent quota caches; only new public FILE admission is config-gated. */
    public synchronized boolean tryAcquire(String publicId, String sourceAddress, RouteKind routeKind) {
        long now = clock.millis();
        RouteKind kind = routeKind == null ? RouteKind.SUBMISSION : routeKind;
        boolean publicFile = kind != RouteKind.SUBMISSION;
        BucketCache formBuckets = publicFile ? publicFileFormBuckets : submissionFormBuckets;
        BucketCache sourceBuckets = publicFile ? publicFileSourceBuckets : submissionSourceBuckets;
        formBuckets.removeExpired(now, bucketTtlMillis);
        sourceBuckets.removeExpired(now, bucketTtlMillis);
        if (kind == RouteKind.PUBLIC_FILE_ADMISSION && !publicFileLimitsValid) {
            return false;
        }
        String formKey = bucketKey(publicId);
        String sourceKey = bucketKey(sourceAddress);
        int effectiveFormLimit = publicFile ? publicFileFormLimit : formLimit;
        int effectiveSourceLimit = publicFile ? publicFileSourceLimit : sourceLimit;
        Bucket form = formBuckets.get(formKey);
        Bucket source = sourceBuckets.get(sourceKey);
        if ((form == null && formBuckets.isFull())
                || (source == null && sourceBuckets.isFull())
                || (form != null && !form.allows(effectiveFormLimit, now))
                || (source != null && !source.allows(effectiveSourceLimit, now))) {
            return false;
        }

        form = formBuckets.getOrCreate(formKey, now);
        source = sourceBuckets.getOrCreate(sourceKey, now);
        form.acquire(now);
        source.acquire(now);
        return true;
    }

    public enum RouteKind {
        SUBMISSION,
        /** Existing draft upload, status, and withdraw routes; invalid config uses fixed fallback limits. */
        PUBLIC_FILE,
        /** New draft/candidate admission; invalid public FILE configuration fails closed here only. */
        PUBLIC_FILE_ADMISSION
    }

    private static void validateLimit(int value, String description) {
        if (!isValidLimit(value)) {
            throw new IllegalArgumentException("marketing form " + description
                    + " rate limit must be between 1 and 10000");
        }
    }

    private static boolean isValidLimit(int value) {
        return value >= 1 && value <= 10_000;
    }

    private static void validateCapacity(int value, String description) {
        if (value < 1 || value > 1_000_000) {
            throw new IllegalArgumentException("marketing form " + description
                    + " bucket capacity is invalid");
        }
    }

    private static String bucketKey(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
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
                Bucket bucket = iterator.next();
                if (now - bucket.lastActivity >= ttlMillis) {
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
