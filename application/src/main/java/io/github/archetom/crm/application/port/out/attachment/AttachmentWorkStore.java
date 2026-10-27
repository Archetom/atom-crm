package io.github.archetom.crm.application.port.out.attachment;

import io.github.archetom.crm.domain.model.AttachmentContentEvidence;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Durable non-domain attachment control plane. Calls are made inside the application transaction;
 * adapters must not open nested transactions.
 */
public interface AttachmentWorkStore {

    void createContentPlan(TenantId tenantId, AttachmentFileId fileId, long contentVersion, long intentVersion,
                           StorageBindingRef binding, PlannedObjectLocator locator,
                           byte[] storageGeneration, long attempt, Instant deadline);

    Optional<ContentPlan> findContentPlan(TenantId tenantId, AttachmentFileId fileId);

    void recordStored(TenantId tenantId, AttachmentFileId fileId, long contentVersion,
                      long attempt, StoredObjectIdentity identity,
                      AttachmentContentEvidence evidence, Instant storedAt);

    void createJobIfAbsent(JobRequest request);

    /**
     * Claims work or atomically dead-letters it and creates a fresh terminal-retention job.
     * The replacement deadline is intentionally independent of the expired source deadline.
     */
    List<LeasedJob> claimDueJobs(JobKind kind, byte[] leaseOwner, Instant now,
                                 Instant leaseUntil, int limit, int maxAttempts,
                                 Instant terminalRecoveryDeadline);

    boolean recordJobEffect(JobFence fence, byte[] resultDigest, Instant now);

    /** Locks and verifies the lease/fence inside the same transaction as the domain mutation. */
    boolean assertLeaseCurrent(JobFence fence, Instant now);

    /** Read-only preflight for external I/O; it must never participate in a lock order. */
    boolean isLeaseCurrent(JobFence fence, Instant now);

    /** Extends only the exact fenced lease; it never reads or locks an attachment subject. */
    boolean heartbeat(JobFence fence, Instant leaseUntil, Instant now);

    boolean settleJob(JobFence fence, byte[] resultDigest, Instant now);

    /** Pauses the current lease without consuming an attempt or scheduling terminal recovery. */
    boolean deferPausedJob(JobFence fence, Instant now);

    /** Rearms paused inspection/publication jobs when their Customer or Contact leaves a pool. */
    void resumePausedJobs(TenantId tenantId, AttachmentFileId fileId,
                          Instant notBefore, Instant deadline, Instant now);

    /** Atomically dead-letters an exhausted job and creates terminal retention recovery. */
    JobRelease releaseJobForRetry(JobFence fence, JobKind kind, Instant notBefore, String safeReason,
                                  Instant now, int maxAttempts, Instant terminalRecoveryDeadline);

    ReadLease acquireReadLease(TenantId tenantId, AttachmentFileId fileId, long contentVersion,
                               Instant expiresAt, Instant now);

    void releaseReadLease(ReadLease lease, Instant now);

    boolean hasActiveReadLease(TenantId tenantId, AttachmentFileId fileId, long contentVersion, Instant now);

    /** Checked, tenant-row-serialized counters; every negative transition fails closed. */
    void adjustUsage(TenantId tenantId, UsageDelta delta, Instant now);

    /**
     * Atomically applies a durable reservation only if every tenant quota remains within the
     * validated runtime limits. A {@code false} result has no side effect.
     */
    boolean reserveUsage(TenantId tenantId, UsageDelta delta, UsageLimits limits, Instant now);

    record ContentPlan(TenantId tenantId, AttachmentFileId fileId, long contentVersion, long intentVersion,
                       StorageBindingRef binding, PlannedObjectLocator locator,
                       byte[] storageGeneration, long attempt, StoredObjectIdentity storedIdentity,
                       AttachmentContentEvidence evidence, Instant deadline) { }
    record JobRequest(TenantId tenantId, AttachmentFileId fileId, long contentVersion,
                      JobKind kind, long generation, Instant notBefore, Instant deadline) { }
    /**
     * {@code effectRecorded} means a prior worker durably recorded its external, idempotent
     * effect but crashed before the state transition. A retry must resume the confirmation step
     * instead of issuing that effect a second time.
     */
    record LeasedJob(JobFence fence, JobKind kind, Instant deadline, boolean effectRecorded,
                     boolean terminal) { }
    record JobFence(TenantId tenantId, AttachmentFileId fileId, long contentVersion,
                    long jobId, long generation, byte[] leaseOwner, long leaseFence) { }
    record ReadLease(byte[] leaseId, TenantId tenantId, AttachmentFileId fileId, long contentVersion,
                     Instant expiresAt) {
        public ReadLease { leaseId = leaseId == null ? null : leaseId.clone(); }
        @Override public byte[] leaseId() { return leaseId == null ? null : leaseId.clone(); }
    }
    record UsageDelta(long readyBytes, long quarantineBytes, long pendingBytes, long pendingIntents,
                      long activeUploads, long activeInspections, long activeDownloads) { }
    record UsageLimits(long maxReadyBytes, long maxQuarantineBytes, long maxPendingIntents,
                       long maxActiveUploads, long maxActiveInspections, long maxActiveDownloads,
                       long globalReserveBytes, long deletionControlReserve) {
        public UsageLimits {
            if (maxReadyBytes < 1 || maxQuarantineBytes < 1 || maxPendingIntents < 1
                    || maxActiveUploads < 1 || maxActiveInspections < 1 || maxActiveDownloads < 1
                    || globalReserveBytes < 1 || deletionControlReserve < 0
                    || deletionControlReserve >= globalReserveBytes) {
                throw new IllegalArgumentException("attachment usage limits are invalid");
            }
        }
    }
    enum JobKind {
        UPLOAD_RECONCILIATION, INCOMPLETE_ABORT, INSPECTION,
        PUBLICATION, DELETION, RETENTION_SWEEP
    }
    long TERMINAL_RECOVERY_GENERATION = 10_000L;
    static long terminalRecoveryGeneration(JobKind sourceKind, long sourceJobId) {
        if (sourceKind == null || sourceJobId < 1) {
            throw new IllegalArgumentException("attachment terminal origin is invalid");
        }
        try {
            return Math.addExact(TERMINAL_RECOVERY_GENERATION, Math.addExact(
                    Math.multiplyExact(sourceJobId, JobKind.values().length), sourceKind.ordinal() + 1L));
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("attachment terminal generation overflow", exception);
        }
    }
    enum JobRelease { RELEASED, DEAD, STALE }
}
