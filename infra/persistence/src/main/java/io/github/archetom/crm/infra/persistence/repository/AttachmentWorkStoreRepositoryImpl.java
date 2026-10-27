package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.port.out.attachment.PlannedObjectLocator;
import io.github.archetom.crm.application.port.out.attachment.StorageBindingRef;
import io.github.archetom.crm.application.port.out.attachment.StoredObjectIdentity;
import io.github.archetom.crm.domain.model.AttachmentContentEvidence;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AttachmentJobMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AttachmentContentMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AttachmentReadLeaseMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AttachmentTenantUsageMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AttachmentGlobalUsageMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentContentPO;
import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentJobPO;
import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentReadLeasePO;
import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentTenantUsagePO;
import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentGlobalUsagePO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** MySQL durable plan/job control plane, including lease fence compare-and-set commits. */
@Repository
@RequiredArgsConstructor
public class AttachmentWorkStoreRepositoryImpl implements AttachmentWorkStore {
    private final AttachmentContentMapper contentMapper;
    private final AttachmentJobMapper jobMapper;
    private final AttachmentReadLeaseMapper readLeaseMapper;
    private final AttachmentTenantUsageMapper usageMapper;
    private final AttachmentGlobalUsageMapper globalUsageMapper;

    @Override
    public void createContentPlan(TenantId tenantId, AttachmentFileId fileId, long contentVersion, long intentVersion,
                                  StorageBindingRef binding, PlannedObjectLocator locator,
                                  byte[] storageGeneration, long attempt, Instant deadline) {
        require(tenantId, fileId, contentVersion, attempt, deadline);
        if (intentVersion < 0) throw new IllegalArgumentException("attachment intent version is invalid");
        AttachmentContentPO po = new AttachmentContentPO();
        po.setTenantId(tenantId.getValue()); po.setFileId(fileId.getValue());
        po.setContentVersion(contentVersion); po.setIntentVersion(intentVersion); po.setBindingRef(binding.copyValue());
        po.setPlannedLocator(locator.copyValue()); po.setStorageGeneration(storageGeneration.clone());
        po.setAttempt(attempt); po.setUploadDeadline(time(deadline)); po.setVersion(0L);
        if (contentMapper.insert(po) != 1) throw new IllegalStateException("attachment content plan insert failed");
    }

    @Override
    public Optional<ContentPlan> findContentPlan(TenantId tenantId, AttachmentFileId fileId) {
        if (tenantId == null || fileId == null) return Optional.empty();
        AttachmentContentPO po = contentMapper.selectOne(new LambdaQueryWrapper<AttachmentContentPO>()
                .eq(AttachmentContentPO::getTenantId, tenantId.getValue())
                .eq(AttachmentContentPO::getFileId, fileId.getValue()));
        return Optional.ofNullable(po).map(this::plan);
    }

    @Override
    public void recordStored(TenantId tenantId, AttachmentFileId fileId, long contentVersion,
                             long attempt, StoredObjectIdentity identity,
                             AttachmentContentEvidence evidence, Instant storedAt) {
        require(tenantId, fileId, contentVersion, attempt, storedAt);
        if (identity == null || evidence == null) throw new IllegalArgumentException("stored evidence is required");
        int changed = contentMapper.update(null, new LambdaUpdateWrapper<AttachmentContentPO>()
                .set(AttachmentContentPO::getStoredIdentity, identity.copyValue())
                .set(AttachmentContentPO::getActualBytes, evidence.actualBytes())
                .set(AttachmentContentPO::getSha256, evidence.sha256())
                .set(AttachmentContentPO::getStoredTime, time(storedAt))
                .setSql("version = version + 1")
                .eq(AttachmentContentPO::getTenantId, tenantId.getValue())
                .eq(AttachmentContentPO::getFileId, fileId.getValue())
                .eq(AttachmentContentPO::getContentVersion, contentVersion)
                .eq(AttachmentContentPO::getAttempt, attempt)
                .isNull(AttachmentContentPO::getStoredIdentity));
        if (changed != 1) throw new IllegalStateException("attachment stored evidence was stale");
    }

    @Override
    @Transactional
    public void createJobIfAbsent(JobRequest request) {
        if (request == null || request.tenantId() == null || request.fileId() == null
                || request.kind() == null || request.generation() < 1 || request.contentVersion() < 1
                || request.notBefore() == null || request.deadline() == null) {
            throw new IllegalArgumentException("attachment job request is invalid");
        }
        insertJobIfAbsent(request);
    }

    private void insertJobIfAbsent(JobRequest request) {
        AttachmentJobPO po = new AttachmentJobPO();
        po.setTenantId(request.tenantId().getValue()); po.setFileId(request.fileId().getValue());
        po.setContentVersion(request.contentVersion()); po.setKind(request.kind().name());
        po.setState("PENDING"); po.setGeneration(request.generation()); po.setAttempt(0L);
        po.setNotBefore(time(request.notBefore())); po.setDeadline(time(request.deadline()));
        po.setLeaseFence(0L); po.setVersion(0L);
        try { jobMapper.insert(po); } catch (DuplicateKeyException ignored) { /* durable idempotency key */ }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<LeasedJob> claimDueJobs(JobKind kind, byte[] leaseOwner, Instant now,
                                         Instant leaseUntil, int limit, int maxAttempts,
                                         Instant terminalRecoveryDeadline) {
        if (kind == null || leaseOwner == null || leaseOwner.length < 1 || leaseOwner.length > 128
                || now == null || leaseUntil == null || !leaseUntil.isAfter(now) || limit < 1 || limit > 25
                || maxAttempts < 1 || terminalRecoveryDeadline == null || !terminalRecoveryDeadline.isAfter(now)) {
            throw new IllegalArgumentException("attachment job claim is invalid");
        }
        LocalDateTime current = time(now);
        List<AttachmentJobPO> candidates = jobMapper.selectDueForUpdate(kind.name(), current, limit);
        return candidates.stream().map(value -> {
            boolean terminal = !instant(value.getDeadline()).isAfter(now) || value.getAttempt() >= maxAttempts;
            if (terminal) {
                if (jobMapper.deadLetterCandidate(value.getId(), current) != 1) return null;
                enqueueTerminalRecovery(value.getTenantId(), value.getFileId(), value.getContentVersion(),
                        JobKind.valueOf(value.getKind()), value.getId(), now, terminalRecoveryDeadline);
                return new LeasedJob(new JobFence(new TenantId(value.getTenantId()),
                        new AttachmentFileId(value.getFileId()), value.getContentVersion(), value.getId(),
                        value.getGeneration(), leaseOwner.clone(), value.getLeaseFence()),
                        JobKind.valueOf(value.getKind()), instant(value.getDeadline()),
                        value.getResultDigest() != null, true);
            }
            if (jobMapper.lease(value.getId(), leaseOwner, time(leaseUntil), current) != 1) return null;
            return new LeasedJob(new JobFence(new TenantId(value.getTenantId()),
                    new AttachmentFileId(value.getFileId()), value.getContentVersion(), value.getId(),
                    value.getGeneration(), leaseOwner.clone(), value.getLeaseFence() + 1),
                    JobKind.valueOf(value.getKind()), instant(value.getDeadline()),
                    value.getResultDigest() != null, false);
        }).filter(java.util.Objects::nonNull).toList();
    }

    @Override public boolean recordJobEffect(JobFence fence, byte[] resultDigest, Instant now) {
        return fence != null && now != null && jobMapper.recordEffect(fence.jobId(),
                fence.tenantId().getValue(), fence.fileId().getValue(), fence.contentVersion(),
                fence.generation(), fence.leaseOwner(), fence.leaseFence(), resultDigest, time(now)) == 1;
    }
    @Override public boolean assertLeaseCurrent(JobFence fence, Instant now) {
        return fence != null && now != null && jobMapper.selectCurrentLeaseForUpdate(fence.jobId(),
                fence.tenantId().getValue(), fence.fileId().getValue(), fence.contentVersion(),
                fence.generation(), fence.leaseOwner(), fence.leaseFence(), time(now)) != null;
    }
    @Override public boolean isLeaseCurrent(JobFence fence, Instant now) {
        return fence != null && now != null && jobMapper.existsCurrentLease(fence.jobId(),
                fence.tenantId().getValue(), fence.fileId().getValue(), fence.contentVersion(),
                fence.generation(), fence.leaseOwner(), fence.leaseFence(), time(now));
    }
    @Override public boolean heartbeat(JobFence fence, Instant leaseUntil, Instant now) {
        if (fence == null || leaseUntil == null || now == null || !leaseUntil.isAfter(now)) return false;
        return jobMapper.heartbeat(fence.jobId(), fence.tenantId().getValue(), fence.fileId().getValue(),
                fence.contentVersion(), fence.generation(), fence.leaseOwner(), fence.leaseFence(),
                time(leaseUntil), time(now)) == 1;
    }
    @Override public boolean settleJob(JobFence fence, byte[] resultDigest, Instant now) {
        return finish(fence, "SETTLED", resultDigest, now);
    }
    @Override public boolean deferPausedJob(JobFence fence, Instant now) {
        if (fence == null || now == null) return false;
        return jobMapper.deferPaused(fence.jobId(), fence.tenantId().getValue(), fence.fileId().getValue(),
                fence.contentVersion(), fence.generation(), fence.leaseOwner(), fence.leaseFence(), time(now)) == 1;
    }
    @Override public void resumePausedJobs(TenantId tenantId, AttachmentFileId fileId,
                                           Instant notBefore, Instant deadline, Instant now) {
        if (tenantId == null || fileId == null || notBefore == null || deadline == null || now == null
                || !deadline.isAfter(notBefore)) {
            throw new IllegalArgumentException("attachment paused job resume is invalid");
        }
        jobMapper.resumePaused(tenantId.getValue(), fileId.getValue(), time(notBefore), time(deadline), time(now));
    }
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobRelease releaseJobForRetry(JobFence fence, JobKind kind, Instant notBefore, String safeReason,
                                                    Instant now, int maxAttempts,
                                                    Instant terminalRecoveryDeadline) {
        if (fence == null || kind == null || notBefore == null || now == null || maxAttempts < 1
                || terminalRecoveryDeadline == null || !terminalRecoveryDeadline.isAfter(now)) return JobRelease.STALE;
        if (jobMapper.deadLetterLeased(fence.jobId(), fence.tenantId().getValue(), fence.fileId().getValue(),
                fence.contentVersion(), fence.generation(), fence.leaseOwner(), fence.leaseFence(),
                time(notBefore), maxAttempts, time(now)) == 1) {
            enqueueTerminalRecovery(fence.tenantId().getValue(), fence.fileId().getValue(),
                    fence.contentVersion(), kind, fence.jobId(), now, terminalRecoveryDeadline);
            return JobRelease.DEAD;
        }
        return jobMapper.release(fence.jobId(),
                fence.tenantId().getValue(), fence.fileId().getValue(), fence.contentVersion(),
                fence.generation(), fence.leaseOwner(), fence.leaseFence(), time(notBefore), time(now)) == 1
                ? JobRelease.RELEASED : JobRelease.STALE;
    }

    @Override
    public ReadLease acquireReadLease(TenantId tenantId, AttachmentFileId fileId, long contentVersion,
                                      Instant expiresAt, Instant now) {
        if (tenantId == null || fileId == null || contentVersion < 1 || expiresAt == null || now == null
                || !expiresAt.isAfter(now)) throw new IllegalArgumentException("attachment read lease is invalid");
        byte[] leaseId = uuidBytes(UUID.randomUUID());
        AttachmentReadLeasePO po = new AttachmentReadLeasePO();
        po.setLeaseId(leaseId); po.setTenantId(tenantId.getValue()); po.setFileId(fileId.getValue());
        po.setContentVersion(contentVersion); po.setLeaseFence(1L); po.setState("OPEN");
        po.setExpiresAt(time(expiresAt));
        if (readLeaseMapper.insert(po) != 1) throw new IllegalStateException("attachment read lease insert failed");
        return new ReadLease(leaseId, tenantId, fileId, contentVersion, expiresAt);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseReadLease(ReadLease lease, Instant now) {
        if (lease == null || lease.leaseId() == null || now == null) return;
        // All counter paths acquire global -> lease -> tenant. Do not release the lease first:
        // a concurrent reservation expires leases after locking global and would otherwise form
        // global -> lease / lease -> global deadlock cycle.
        globalUsageMapper.insertIfAbsent();
        if (globalUsageMapper.selectForUpdate() == null) {
            throw new IllegalStateException("attachment global usage row is unavailable");
        }
        if (readLeaseMapper.release(lease.leaseId(), lease.tenantId().getValue(), lease.fileId().getValue(),
                lease.contentVersion(), time(now)) != 1) return;
        usageMapper.insertIfAbsent(lease.tenantId().getValue());
        AttachmentTenantUsagePO current = usageMapper.selectForUpdate(lease.tenantId().getValue());
        if (current == null) throw new IllegalStateException("attachment usage row is unavailable");
        long downloads = add(current.getActiveDownloads(), -1);
        if (usageMapper.replaceChecked(lease.tenantId().getValue(), current.getVersion(),
                current.getReadyBytes(), current.getQuarantineBytes(), current.getPendingBytes(),
                current.getPendingIntents(), current.getActiveUploads(), current.getActiveInspections(), downloads,
                time(now)) != 1) {
            throw new IllegalStateException("attachment usage update was stale");
        }
    }

    @Override
    public boolean hasActiveReadLease(TenantId tenantId, AttachmentFileId fileId, long contentVersion, Instant now) {
        return tenantId != null && fileId != null && contentVersion > 0 && now != null
                && readLeaseMapper.existsOpen(tenantId.getValue(), fileId.getValue(), contentVersion, time(now));
    }

    @Override
    @Transactional
    public void adjustUsage(TenantId tenantId, UsageDelta delta, Instant now) {
        applyUsage(tenantId, delta, null, now, false);
    }

    @Override
    @Transactional
    public boolean reserveUsage(TenantId tenantId, UsageDelta delta, UsageLimits limits, Instant now) {
        if (limits == null) throw new IllegalArgumentException("attachment usage limits are required");
        return applyUsage(tenantId, delta, limits, now, true);
    }

    private boolean applyUsage(TenantId tenantId, UsageDelta delta, UsageLimits limits, Instant now,
                               boolean enforceLimits) {
        if (tenantId == null || delta == null || now == null) {
            throw new IllegalArgumentException("attachment usage change is invalid");
        }
        // Keep the lock order global -> tenant for every reserve/release.  A global reserve is
        // otherwise merely a per-tenant limit under concurrent multi-tenant admission.
        globalUsageMapper.insertIfAbsent();
        AttachmentGlobalUsagePO global = globalUsageMapper.selectForUpdate();
        if (global == null) throw new IllegalStateException("attachment global usage row is unavailable");
        int expiredDownloads = readLeaseMapper.expireOpen(tenantId.getValue(), time(now));
        usageMapper.insertIfAbsent(tenantId.getValue());
        AttachmentTenantUsagePO current = usageMapper.selectForUpdate(tenantId.getValue());
        if (current == null) throw new IllegalStateException("attachment usage row is unavailable");
        long ready = add(current.getReadyBytes(), delta.readyBytes());
        long quarantine = add(current.getQuarantineBytes(), delta.quarantineBytes());
        long pending = add(current.getPendingBytes(), delta.pendingBytes());
        long intents = add(current.getPendingIntents(), delta.pendingIntents());
        long uploads = add(current.getActiveUploads(), delta.activeUploads());
        long inspections = add(current.getActiveInspections(), delta.activeInspections());
        long downloadsAfterExpiry = add(current.getActiveDownloads(), -expiredDownloads);
        long downloads = add(downloadsAfterExpiry, delta.activeDownloads());
        long globalReady = add(global.getReadyBytes(), delta.readyBytes());
        long globalQuarantine = add(global.getQuarantineBytes(), delta.quarantineBytes());
        long globalPending = add(global.getPendingBytes(), delta.pendingBytes());
        if (enforceLimits && !withinLimits(ready, quarantine, intents, uploads, inspections, downloads,
                globalReady, globalQuarantine, globalPending, limits)) {
            // expireOpen changed durable lease state in this transaction. Its matching counter
            // decrement must commit even when the requested new reservation is rejected; leaving
            // it behind would make released leases permanently consume download capacity.
            if (expiredDownloads > 0 && usageMapper.replaceChecked(tenantId.getValue(), current.getVersion(),
                    current.getReadyBytes(), current.getQuarantineBytes(), current.getPendingBytes(),
                    current.getPendingIntents(), current.getActiveUploads(), current.getActiveInspections(),
                    downloadsAfterExpiry, time(now)) != 1) {
                throw new IllegalStateException("attachment expired download settlement was stale");
            }
            return false;
        }
        if (globalUsageMapper.replaceChecked(global.getVersion(), globalReady, globalQuarantine, globalPending,
                time(now)) != 1) {
            throw new IllegalStateException("attachment global usage update was stale");
        }
        if (usageMapper.replaceChecked(tenantId.getValue(), current.getVersion(), ready, quarantine, pending,
                intents, uploads, inspections, downloads, time(now)) != 1) {
            throw new IllegalStateException("attachment usage update was stale");
        }
        return true;
    }

    private boolean finish(JobFence fence, String state, byte[] digest, Instant now) {
        return fence != null && now != null && jobMapper.finish(fence.jobId(), fence.tenantId().getValue(),
                fence.fileId().getValue(), fence.contentVersion(), fence.generation(), fence.leaseOwner(),
                fence.leaseFence(), state, digest, time(now)) == 1;
    }
    /** The source job row remains locked in the dead-letter transaction when this is called. */
    private void enqueueTerminalRecovery(long tenantId, long fileId, long contentVersion, JobKind sourceKind,
                                         long sourceJobId,
                                         Instant now, Instant deadline) {
        long recoveryGeneration = AttachmentWorkStore.terminalRecoveryGeneration(sourceKind, sourceJobId);
        insertJobIfAbsent(new JobRequest(new TenantId(tenantId), new AttachmentFileId(fileId), contentVersion,
                JobKind.RETENTION_SWEEP, recoveryGeneration, now, deadline));
    }
    private ContentPlan plan(AttachmentContentPO po) {
        AttachmentContentEvidence evidence = po.getStoredIdentity() == null ? null
                : new AttachmentContentEvidence(po.getActualBytes(), po.getSha256(), po.getContentVersion());
        return new ContentPlan(new TenantId(po.getTenantId()), new AttachmentFileId(po.getFileId()),
                po.getContentVersion(), po.getIntentVersion(), new StorageBindingRef(po.getBindingRef()),
                new PlannedObjectLocator(po.getPlannedLocator()), po.getStorageGeneration(), po.getAttempt(),
                po.getStoredIdentity() == null ? null : new StoredObjectIdentity(po.getStoredIdentity()), evidence,
                instant(po.getUploadDeadline()));
    }
    private static void require(TenantId tenantId, AttachmentFileId fileId, long contentVersion,
                                long attempt, Instant time) {
        if (tenantId == null || fileId == null || contentVersion < 1 || attempt < 1 || time == null) {
            throw new IllegalArgumentException("attachment work subject is invalid");
        }
    }
    private static LocalDateTime time(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static Instant instant(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC); }
    private static long add(Long value, long delta) {
        try {
            long next = Math.addExact(value == null ? 0L : value, delta);
            if (next < 0) throw new IllegalStateException("attachment usage would become negative");
            return next;
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("attachment usage overflow", exception);
        }
    }
    private static boolean withinLimits(long ready, long quarantine, long intents,
                                        long uploads, long inspections, long downloads,
                                        long globalReady, long globalQuarantine, long globalPending,
                                        UsageLimits limits) {
        if (ready > limits.maxReadyBytes() || quarantine > limits.maxQuarantineBytes()
                || intents > limits.maxPendingIntents() || uploads > limits.maxActiveUploads()
                || inspections > limits.maxActiveInspections() || downloads > limits.maxActiveDownloads()) {
            return false;
        }
        long occupied = add(add(globalReady, globalQuarantine), globalPending);
        return occupied <= limits.globalReserveBytes() - limits.deletionControlReserve();
    }
    private static byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()).array();
    }
}
