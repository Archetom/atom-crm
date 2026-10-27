package io.github.archetom.crm.application.port.out.attachment;

import java.time.Duration;

/** Validated runtime limits supplied by the composition root; no production fallback defaults exist. */
public record AttachmentRuntimePolicy(
        boolean enabled, long maxFileBytes, long maxReadyBytes, long maxQuarantineBytes,
        long maxPendingIntents, int maxActiveUploads, int maxActiveInspections, int maxActiveDownloads,
        long globalReserveBytes, long deletionControlReserve, Duration intentTtl, Duration uploadOpenTimeout,
        Duration uploadIdleTimeout, Duration uploadTotalTimeout, Duration inspectionTotalTimeout,
        Duration downloadOpenTimeout, Duration downloadIdleTimeout, Duration downloadTotalTimeout,
        Duration jobLease, Duration jobHeartbeat, int maxAttempts, Duration quarantineRetention,
        Duration tombstoneRetention, int claimSize) {

    public AttachmentRuntimePolicy {
        if (enabled && (maxFileBytes < 1 || maxFileBytes > 16_777_216
                || maxReadyBytes < 1 || maxQuarantineBytes < 1 || maxPendingIntents < 1
                || maxActiveUploads < 1 || maxActiveInspections < 1 || maxActiveDownloads < 1
                || globalReserveBytes < 1 || deletionControlReserve < 0
                || deletionControlReserve >= globalReserveBytes
                || !positive(intentTtl) || !positive(uploadOpenTimeout) || !positive(uploadIdleTimeout)
                || !positive(uploadTotalTimeout) || !positive(inspectionTotalTimeout)
                || !positive(downloadOpenTimeout) || !positive(downloadIdleTimeout)
                || !positive(downloadTotalTimeout) || !positive(jobLease) || !positive(jobHeartbeat)
                || jobHeartbeat.compareTo(jobLease) >= 0
                || maxAttempts < 1 || !positive(quarantineRetention) || !positive(tombstoneRetention)
                || claimSize < 1 || claimSize > 25)) {
            throw new IllegalArgumentException("attachment runtime policy is incomplete");
        }
    }

    private static boolean positive(Duration value) {
        return value != null && !value.isNegative() && !value.isZero();
    }

    public static AttachmentRuntimePolicy disabled() {
        return new AttachmentRuntimePolicy(false, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                null, null, null, null, null, null, null, null, null, null,
                0, null, null, 0);
    }

    public AttachmentWorkStore.UsageLimits usageLimits() {
        if (!enabled) throw new IllegalStateException("attachment runtime policy is disabled");
        return new AttachmentWorkStore.UsageLimits(maxReadyBytes, maxQuarantineBytes, maxPendingIntents,
                maxActiveUploads, maxActiveInspections, maxActiveDownloads,
                globalReserveBytes, deletionControlReserve);
    }
}
