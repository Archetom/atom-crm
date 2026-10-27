package io.github.archetom.crm.application.service.attachment.worker;

import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorageReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccessRegistry;
import io.github.archetom.crm.application.service.impl.MarketingFormPolicy;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.MarketingFileDraft;
import io.github.archetom.crm.domain.entity.MarketingForm;
import io.github.archetom.crm.domain.entity.ProductImageDraft;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentByteState;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.repository.MarketingFileDraftRepository;
import io.github.archetom.crm.domain.repository.MarketingFormRepository;
import io.github.archetom.crm.domain.repository.ProductImageDraftRepository;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared worker guard: DB claim and effect commits are separate from all external I/O. */
@Component
public class AttachmentWorkerSupport implements DisposableBean {
    private static final int LEASE_HEARTBEAT_THREADS = 4;

    /**
     * Every in-flight job renews its own lease here. A single thread would let one slow renewal
     * delay the others, expiring healthy leases and letting another replica reclaim their jobs
     * while their external I/O is still running; the database fence protects the stored state but
     * not the repeated storage or scanner call. Virtual threads keep a blocked renewal from
     * occupying a carrier while it waits on the database.
     */
    private final ScheduledExecutorService leaseHeartbeats = Executors.newScheduledThreadPool(
            LEASE_HEARTBEAT_THREADS,
            Thread.ofVirtual().name("attachment-lease-heartbeat-", 0).factory());
    private final AttachmentWorkStore workStore;
    private final AttachmentFileRepository fileRepository;
    private final FileFieldRepository fileFieldRepository;
    private final MarketingFileDraftRepository marketingFileDraftRepository;
    private final ProductImageDraftRepository productImageDraftRepository;
    private final MarketingFormRepository marketingFormRepository;
    private final CustomizationObjectLock customizationObjectLock;
    private final MarketingFormPolicy marketingFormPolicy;
    private final AttachmentTargetAccessRegistry targetAccessRegistry;
    private final AttachmentStorageReadinessResolver storageReadinessResolver;
    private final AttachmentRuntimePolicy policy;
    private final Clock clock;

    @Autowired
    public AttachmentWorkerSupport(AttachmentWorkStore workStore, AttachmentFileRepository fileRepository,
                                   FileFieldRepository fileFieldRepository,
                                   MarketingFileDraftRepository marketingFileDraftRepository,
                                   ProductImageDraftRepository productImageDraftRepository,
                                   MarketingFormRepository marketingFormRepository,
                                   CustomizationObjectLock customizationObjectLock,
                                   MarketingFormPolicy marketingFormPolicy,
                                   AttachmentTargetAccessRegistry targetAccessRegistry,
                                   AttachmentStorageReadinessResolver storageReadinessResolver,
                                   AttachmentRuntimePolicy policy, Clock clock) {
        this.workStore = workStore;
        this.fileRepository = fileRepository;
        this.fileFieldRepository = fileFieldRepository;
        this.marketingFileDraftRepository = marketingFileDraftRepository;
        this.productImageDraftRepository = productImageDraftRepository;
        this.marketingFormRepository = marketingFormRepository;
        this.customizationObjectLock = customizationObjectLock;
        this.marketingFormPolicy = marketingFormPolicy;
        this.targetAccessRegistry = targetAccessRegistry;
        this.storageReadinessResolver = storageReadinessResolver;
        this.policy = policy;
        this.clock = clock;
    }

    /** Compatibility constructor for B24a/b unit fixtures which never exercise staged files. */
    public AttachmentWorkerSupport(AttachmentWorkStore workStore, AttachmentFileRepository fileRepository,
                                   AttachmentTargetAccessRegistry targetAccessRegistry,
                                   AttachmentStorageReadinessResolver storageReadinessResolver,
                                   AttachmentRuntimePolicy policy, Clock clock) {
        this(workStore, fileRepository, null, null, null, null, null, null,
                targetAccessRegistry, storageReadinessResolver, policy, clock);
    }

    /** Compatibility constructor for staged marketing-form worker fixtures. */
    public AttachmentWorkerSupport(AttachmentWorkStore workStore, AttachmentFileRepository fileRepository,
                                   FileFieldRepository fileFieldRepository,
                                   MarketingFileDraftRepository marketingFileDraftRepository,
                                   MarketingFormRepository marketingFormRepository,
                                   CustomizationObjectLock customizationObjectLock,
                                   MarketingFormPolicy marketingFormPolicy,
                                   AttachmentTargetAccessRegistry targetAccessRegistry,
                                   AttachmentStorageReadinessResolver storageReadinessResolver,
                                   AttachmentRuntimePolicy policy, Clock clock) {
        this(workStore, fileRepository, fileFieldRepository, marketingFileDraftRepository, null,
                marketingFormRepository, customizationObjectLock, marketingFormPolicy,
                targetAccessRegistry, storageReadinessResolver, policy, clock);
    }

    public java.util.List<AttachmentWorkStore.LeasedJob> claim(AttachmentWorkStore.JobKind kind,
                                                                byte[] owner) {
        if (!policy.enabled()) return java.util.List.of();
        Instant now = clock.instant();
        // A worker invocation is intentionally serial around external I/O.  Leasing more than
        // one row here would spend attempts while later rows wait behind a slow scanner/storage
        // call, so every invocation owns exactly one durable lease.
        java.util.List<AttachmentWorkStore.LeasedJob> claimed = workStore.claimDueJobs(kind, owner, now,
                now.plus(policy.jobLease()), 1, policy.maxAttempts(),
                now.plus(policy.tombstoneRetention()));
        return claimed.stream().filter(job -> !job.terminal()).toList();
    }
    /** External-I/O preflight only; no row is locked. */
    public boolean leaseMayBeCurrent(AttachmentWorkStore.JobFence fence) {
        return workStore.isLeaseCurrent(fence, now());
    }
    /**
     * Renew before each potentially slow external step.  This only touches the job row, so it
     * cannot create a reverse target/file lock edge.  Operations themselves have independent
     * total deadlines and therefore cannot hold a renewed lease forever.
     */
    public boolean heartbeat(AttachmentWorkStore.LeasedJob job) {
        if (job == null) return false;
        Instant current = now();
        return workStore.heartbeat(job.fence(), current.plus(policy.jobLease()), current);
    }
    /**
     * Runs only during external I/O. It renews the exact job lease without acquiring subject
     * locks; a failed heartbeat forces the caller to discard its external result.
     */
    public LeaseHeartbeat heartbeatGuard(AttachmentWorkStore.LeasedJob job) {
        if (job == null || !heartbeat(job)) return LeaseHeartbeat.lost();
        AtomicBoolean current = new AtomicBoolean(true);
        long intervalMillis = Math.max(1L, policy.jobHeartbeat().toMillis());
        ScheduledFuture<?> task = leaseHeartbeats.scheduleAtFixedRate(() -> {
            try {
                if (!heartbeat(job)) current.set(false);
            } catch (RuntimeException exception) {
                current.set(false);
            }
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        return new LeaseHeartbeat(current, task);
    }
    /** Renewals must stop with the context; a leaked scheduler outlives the beans it renews for. */
    @Override
    public void destroy() {
        leaseHeartbeats.shutdownNow();
    }

    public void settleRequired(AttachmentWorkStore.JobFence fence, byte[] digest) {
        if (!workStore.settleJob(fence, digest, now())) {
            throw new IllegalStateException("attachment job lease was lost before settlement");
        }
    }
    public void retry(AttachmentWorkStore.LeasedJob job, String safeReason) {
        if (job == null) return;
        Instant current = now();
        workStore.releaseJobForRetry(job.fence(), job.kind(), current.plusSeconds(5), safeReason, current,
                policy.maxAttempts(), current.plus(policy.tombstoneRetention()));
    }
    /** Defers a pool-paused job inside the current target/file/job transaction without an attempt. */
    public void deferForPause(AttachmentWorkStore.JobFence fence) {
        if (!workStore.deferPausedJob(fence, now())) {
            throw new IllegalStateException("attachment paused job lease was lost before deferral");
        }
    }
    public Subject lockSubject(AttachmentWorkStore.JobFence fence) {
        TenantId tenantId = fence.tenantId();
        AttachmentFileId fileId = fence.fileId();
        // Route without a relation lock, acquire the typed parent root, then re-read the
        // relation under that root. This matches every human mutation's target-first order.
        AttachmentTarget target = fileRepository.findTargetForFile(tenantId, fileId).orElse(null);
        if (target == null) {
            // Anonymous MarketingForm candidates are also staged, but their exact public draft
            // is the origin and lock root.  They never resolve to a human uploader or target.
            var routedMarketing = marketingFileDraftRepository == null
                    ? java.util.Optional.<io.github.archetom.crm.domain.entity.MarketingFileDraft>empty()
                    : marketingFileDraftRepository.findForStagedFile(tenantId, fileId);
            if (routedMarketing.isPresent()) {
                Subject subject = lockMarketingSubject(tenantId, fileId, routedMarketing.get());
                if (subject != null) return subject;
            }
            var routedProductImage = productImageDraftRepository == null
                    ? java.util.Optional.<ProductImageDraft>empty()
                    : productImageDraftRepository.findForStagedFile(tenantId, fileId);
            if (routedProductImage.isPresent()) {
                Subject subject = lockProductImageSubject(tenantId, fileId, routedProductImage.get());
                if (subject != null) return subject;
            }
            // A FILE candidate has no ordinary attachment relation.  Discover its draft
            // without a lock, then lock draft -> staging row -> file, matching every command
            // that owns a candidate.  Never lock the file before resolving its draft.
            var routedDraft = fileFieldRepository == null ? java.util.Optional.<io.github.archetom.crm.domain.entity.FileFieldDraft>empty()
                    : fileFieldRepository.findDraftForStagedFile(tenantId, fileId);
            if (routedDraft.isPresent()) {
                var draft = fileFieldRepository.findDraftForUpdate(tenantId, routedDraft.get().getId())
                        .orElse(null);
                if (draft != null) {
                    var staged = fileFieldRepository.findStagedFilesForUpdate(tenantId, draft.getId()).stream()
                            .filter(value -> value.file().getId().sameValueAs(fileId)).findFirst().orElse(null);
                    if (staged != null) {
                        boolean active = draft.getState()
                                == io.github.archetom.crm.domain.model.FileFieldDraftState.ACTIVE
                                && now().isBefore(draft.getDeadline().toInstant(java.time.ZoneOffset.UTC));
                        if (!active) {
                            draft.expire(java.time.LocalDateTime.ofInstant(now(), java.time.ZoneOffset.UTC));
                            fileFieldRepository.saveDraft(tenantId, draft);
                            fileFieldRepository.withdrawStagedFiles(tenantId, draft.getId());
                        }
                        return new Subject(tenantId, null, fileId, staged.file(), active, false, true);
                    }
                }
            }
            // BOUND and already-withdrawn FILE values are deliberately reachable by exact
            // tenant-scoped ID so their retention jobs can release bytes and quota.
            AttachmentFile file = fileRepository.findByIdForUpdate(tenantId, fileId)
                    .orElseThrow(() -> new IllegalArgumentException("attachment file is unavailable"));
            return new Subject(tenantId, null, fileId, file, false, false, true);
        }
        var lifecycle = targetAccessRegistry.require(target.type()).lockForWorker(tenantId, target);
        AttachmentTarget lockedTarget = fileRepository.findTargetForFileForUpdate(tenantId, fileId)
                .orElseThrow(() -> new IllegalArgumentException("attachment target is unavailable"));
        if (!target.equals(lockedTarget)) throw new IllegalStateException("attachment target changed while locking");
        AttachmentFile file = fileRepository.findByTargetForUpdate(tenantId, target, fileId)
                .orElseThrow(() -> new IllegalArgumentException("attachment file is unavailable"));
        return new Subject(tenantId, target, fileId, file, lifecycle.attachmentsPermitted(),
                lifecycle.attachmentsPaused(), false);
    }
    /** The job row is always locked only after target -> relation -> file. */
    public Subject lockSubjectAndAssertLease(AttachmentWorkStore.JobFence fence) {
        Subject subject = lockSubject(fence);
        return workStore.assertLeaseCurrent(fence, now()) ? subject : null;
    }
    public byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    public Instant now() { return clock.instant(); }
    public AttachmentWorkStore workStore() { return workStore; }
    public AttachmentFileRepository fileRepository() { return fileRepository; }
    public AttachmentRuntimePolicy policy() { return policy; }
    public boolean planReady(AttachmentWorkStore.ContentPlan plan, AttachmentOperation operation) {
        if (plan == null || operation == null) return false;
        var snapshot = storageReadinessResolver.resolve(plan.tenantId(), operation);
        return snapshot != null && snapshot.isReady(operation, now()) && snapshot.binding() != null
                && Arrays.equals(snapshot.binding().copyValue(), plan.binding().copyValue());
    }

    /**
     * Marketing candidates have no ordinary attachment target. Their parent form is therefore
     * the lock root: form -> LEAD customization guard -> exact draft -> candidate/file.  A
     * queued worker must not keep scanning or reconciling a candidate whose public form was
     * changed, deactivated, or whose stored owner has lost current lead-write access.
     */
    private Subject lockMarketingSubject(TenantId tenantId, AttachmentFileId fileId,
                                         MarketingFileDraft routedDraft) {
        MarketingForm form = marketingFormRepository == null ? null
                : marketingFormRepository.findByIdForUpdate(tenantId, routedDraft.getFormId()).orElse(null);
        if (form != null && customizationObjectLock != null) {
            customizationObjectLock.acquire(tenantId, BusinessObjectType.LEAD);
        }
        MarketingFileDraft draft = marketingFileDraftRepository.findForUpdate(tenantId, routedDraft.getId())
                .orElse(null);
        if (draft == null) return null;
        var stagedFiles = marketingFileDraftRepository.findStagedFilesForUpdate(tenantId, draft.getId());
        var staged = stagedFiles.stream().filter(value -> value.file().getId().sameValueAs(fileId))
                .findFirst().orElse(null);
        if (staged == null) return null;

        Instant instant = now();
        boolean active = isCurrentMarketingDraft(form, draft, instant);
        if (!active) cleanupMarketingDraft(tenantId, draft, stagedFiles, instant);
        return new Subject(tenantId, null, fileId, staged.file(), active, false, true);
    }

    private boolean isCurrentMarketingDraft(MarketingForm form, MarketingFileDraft draft, Instant instant) {
        if (form == null || !form.isActive() || form.getVersion() == null
                || draft.getFormVersion() != form.getVersion()
                || draft.getState() != io.github.archetom.crm.domain.model.MarketingFileDraftState.ACTIVE
                || !instant.isBefore(draft.getDeadline().toInstant(ZoneOffset.UTC))) {
            return false;
        }
        // The full policy re-evaluation covers both current field definition/access policy and
        // the persisted owner's present ability to create a lead. Policy mismatches are expected
        // lifecycle changes; persistence and infrastructure failures still propagate normally.
        try {
            if (marketingFormPolicy == null) return false;
            marketingFormPolicy.validate(form.getTenantId(), form.getOwnerMemberId(), form.getFields(), true);
            return true;
        } catch (NonRetryableApplicationException exception) {
            return false;
        }
    }

    /** Product image candidates remain private until Product creation consumes their exact draft. */
    private Subject lockProductImageSubject(TenantId tenantId, AttachmentFileId fileId,
                                            ProductImageDraft routedDraft) {
        ProductImageDraft draft = productImageDraftRepository.findForUpdate(tenantId, routedDraft.getId())
                .orElse(null);
        if (draft == null) return null;
        var stagedFiles = productImageDraftRepository.findStagedFilesForUpdate(tenantId, draft.getId());
        var staged = stagedFiles.stream().filter(value -> value.file().getId().sameValueAs(fileId))
                .findFirst().orElse(null);
        if (staged == null) return null;
        Instant instant = now();
        boolean active = draft.getState() == io.github.archetom.crm.domain.model.ProductImageDraftState.ACTIVE
                && instant.isBefore(draft.getDeadline().toInstant(ZoneOffset.UTC));
        if (!active) cleanupProductImageDraft(tenantId, draft, stagedFiles, instant);
        return new Subject(tenantId, null, fileId, staged.file(), active, false, true);
    }

    private void cleanupProductImageDraft(TenantId tenantId, ProductImageDraft draft,
                                          java.util.List<ProductImageDraftRepository.StagedFile> stagedFiles,
                                          Instant instant) {
        LocalDateTime current = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        if (draft.getState() == io.github.archetom.crm.domain.model.ProductImageDraftState.ACTIVE) {
            draft.expire(current);
        }
        productImageDraftRepository.withdrawStagedFiles(tenantId, draft.getId());
        for (ProductImageDraftRepository.StagedFile staged : stagedFiles) {
            AttachmentFile file = staged.file();
            file.withdraw(current);
            fileRepository.save(tenantId, file);
            AttachmentWorkStore.JobKind cleanup = file.getByteState() == AttachmentByteState.REMOVING
                    ? AttachmentWorkStore.JobKind.DELETION : AttachmentWorkStore.JobKind.INCOMPLETE_ABORT;
            Instant deadline = instant.plus(policy.tombstoneRetention());
            workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, file.getId(),
                    file.getContentVersion(), cleanup, 1, instant, deadline));
            workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, file.getId(),
                    file.getContentVersion(), AttachmentWorkStore.JobKind.RETENTION_SWEEP,
                    3, deadline, deadline.plus(policy.tombstoneRetention())));
        }
        productImageDraftRepository.save(tenantId, draft);
    }

    /** Turns a policy-invalid public draft into a bounded byte/quota cleanup workflow. */
    private void cleanupMarketingDraft(TenantId tenantId, MarketingFileDraft draft,
                                       java.util.List<MarketingFileDraftRepository.StagedFile> stagedFiles,
                                       Instant instant) {
        LocalDateTime current = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        if (draft.getState() == io.github.archetom.crm.domain.model.MarketingFileDraftState.ACTIVE) {
            if (!draft.expire(current)) draft.withdraw(current);
        }
        marketingFileDraftRepository.withdrawStagedFiles(tenantId, draft.getId());
        for (MarketingFileDraftRepository.StagedFile staged : stagedFiles) {
            AttachmentFile file = staged.file();
            file.withdraw(current);
            fileRepository.save(tenantId, file);
            AttachmentWorkStore.JobKind cleanup = file.getByteState() == AttachmentByteState.REMOVING
                    ? AttachmentWorkStore.JobKind.DELETION : AttachmentWorkStore.JobKind.INCOMPLETE_ABORT;
            Instant deadline = instant.plus(policy.tombstoneRetention());
            workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, file.getId(),
                    file.getContentVersion(), cleanup, 1, instant, deadline));
            workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, file.getId(),
                    file.getContentVersion(), AttachmentWorkStore.JobKind.RETENTION_SWEEP,
                    3, deadline, deadline.plus(policy.tombstoneRetention())));
        }
        marketingFileDraftRepository.save(tenantId, draft);
    }
    public static final class LeaseHeartbeat implements AutoCloseable {
        private final AtomicBoolean current;
        private final ScheduledFuture<?> task;
        private LeaseHeartbeat(AtomicBoolean current, ScheduledFuture<?> task) {
            this.current = current; this.task = task;
        }
        private static LeaseHeartbeat lost() { return new LeaseHeartbeat(new AtomicBoolean(false), null); }
        public boolean isCurrent() { return current.get(); }
        @Override public void close() { if (task != null) task.cancel(false); }
    }
    public record Subject(TenantId tenantId, AttachmentTarget target, AttachmentFileId fileId,
                          AttachmentFile file, boolean attachmentsPermitted,
                          boolean attachmentsPaused, boolean stagedFileField) {
        public Subject(TenantId tenantId, AttachmentTarget target, AttachmentFileId fileId,
                       AttachmentFile file, boolean attachmentsPermitted, boolean attachmentsPaused) {
            this(tenantId, target, fileId, file, attachmentsPermitted, attachmentsPaused, false);
        }
    }
}
