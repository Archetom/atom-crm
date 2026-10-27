package io.github.archetom.crm.application.service.attachment.worker;

import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.model.AttachmentByteState;
import io.github.archetom.crm.domain.model.AttachmentPublicationState;
import io.github.archetom.crm.domain.model.AttachmentUploadOrigin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Reconciles only the durable planned locator after an uncertain store/finalize crash. */
@Component
@RequiredArgsConstructor
public class AttachmentUploadReconciliationWorker implements AttachmentWorker {
    private final AttachmentWorkerSupport support;
    private final AttachmentStorage storage;
    private final CommandServiceTemplate commandTemplate;
    private final byte[] owner = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    @Override public void runOnce() {
        for (var job : support.claim(AttachmentWorkStore.JobKind.UPLOAD_RECONCILIATION, owner)) reconcile(job);
    }
    private void reconcile(AttachmentWorkStore.LeasedJob job) {
        if (!support.leaseMayBeCurrent(job.fence()) || !support.heartbeat(job)) return;
        // Match inspection's pre-I/O form-root recheck. A marketing candidate can be invalidated
        // while this reconciliation job waits in the queue; do not probe storage for it after
        // the form changes, deactivates, or its owner loses current lead-write access.
        if (!admitMarketingReconciliation(job)) return;
        var plan = support.workStore().findContentPlan(job.fence().tenantId(), job.fence().fileId()).orElse(null);
        if (plan == null) { support.settleRequired(job.fence(), support.digest("NO_PLAN")); return; }
        if (!support.planReady(plan, io.github.archetom.crm.application.port.out.attachment.AttachmentOperation.PROBE_EXACT)) {
            retry(job); return;
        }
        AttachmentStorage.ProbeResult probe;
        try (var lease = support.heartbeatGuard(job)) {
            if (!lease.isCurrent()) return;
            probe = storage.probe(new AttachmentStorage.ProbeCommand(plan.binding(), plan.locator(),
                    plan.storedIdentity(), plan.storageGeneration(), job.deadline()));
            if (!lease.isCurrent()) return;
        }
        catch (Exception exception) { retry(job); return; }
        if (probe == null || probe.state() == AttachmentStorage.ProbeState.UNKNOWN) { retry(job); return; }
        if (probe.state() != AttachmentStorage.ProbeState.PRESENT || probe.identity() == null || probe.evidence() == null) {
            support.workStore().createJobIfAbsent(new AttachmentWorkStore.JobRequest(job.fence().tenantId(),
                    job.fence().fileId(), job.fence().contentVersion(), AttachmentWorkStore.JobKind.INCOMPLETE_ABORT,
                    job.fence().generation(), support.now(),
                    support.now().plus(support.policy().tombstoneRetention())));
            support.settleRequired(job.fence(), support.digest("ABSENT_OR_MISMATCH")); return;
        }
        commandTemplate.execute(io.github.archetom.crm.application.operation.UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD,
                new ServiceOperation<>() { @Override public void validate() { }
                    @Override public Void execute() {
                        var subject = support.lockSubjectAndAssertLease(job.fence());
                        if (subject == null) return null;
                        if (subject.file().getByteState() == AttachmentByteState.UPLOADING
                                && subject.file().getPublicationState() == AttachmentPublicationState.PRIVATE) {
                            subject.file().acceptStored(probe.evidence(), subject.file().getDeclaredMediaType(),
                                    LocalDateTime.ofInstant(support.now(), ZoneOffset.UTC));
                            support.fileRepository().save(subject.tenantId(), subject.file());
                            support.workStore().recordStored(subject.tenantId(), subject.fileId(),
                                    subject.file().getContentVersion(), plan.attempt(), probe.identity(),
                                    probe.evidence(), support.now());
                            if (!support.workStore().reserveUsage(subject.tenantId(), new AttachmentWorkStore.UsageDelta(
                                    0, probe.evidence().actualBytes(), -subject.file().getExpectedBytes(), -1,
                                    -1, 1, 0), support.policy().usageLimits(), support.now())) {
                                throw new IllegalStateException("attachment inspection quota is unavailable");
                            }
                            support.workStore().createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.tenantId(),
                                    subject.fileId(), subject.file().getContentVersion(),
                                    AttachmentWorkStore.JobKind.INSPECTION, 1, support.now(),
                                    support.now().plus(support.policy().inspectionTotalTimeout())));
                        } else if (subject.file().getByteState() != AttachmentByteState.STORED) {
                            // The bytes are real after a finalize crash. A withdrawn/expired
                            // intent may not become available again, but the observed exact
                            // identity must go through DELETE_EXACT -> CONFIRM_ABSENCE rather
                            // than the best-effort incomplete abort path.
                            var currentPlan = support.workStore().findContentPlan(subject.tenantId(), subject.fileId())
                                    .orElseThrow();
                            if (currentPlan.storedIdentity() == null) {
                                boolean captured = subject.file().captureStoredForRemoval(probe.evidence(),
                                        subject.file().getDeclaredMediaType(),
                                        LocalDateTime.ofInstant(support.now(), ZoneOffset.UTC));
                                support.workStore().recordStored(subject.tenantId(), subject.fileId(),
                                        subject.file().getContentVersion(), currentPlan.attempt(), probe.identity(),
                                        probe.evidence(), support.now());
                                if (captured) {
                                    support.workStore().adjustUsage(subject.tenantId(), new AttachmentWorkStore.UsageDelta(
                                            0, probe.evidence().actualBytes(), -subject.file().getExpectedBytes(),
                                            -1, -1, 0, 0), support.now());
                                    support.fileRepository().save(subject.tenantId(), subject.file());
                                }
                            }
                            support.workStore().createJobIfAbsent(new AttachmentWorkStore.JobRequest(
                                    subject.tenantId(), subject.fileId(), subject.file().getContentVersion(),
                                    AttachmentWorkStore.JobKind.DELETION, 2, support.now(),
                                    support.now().plus(support.policy().tombstoneRetention())));
                        }
                        support.settleRequired(job.fence(), support.digest("RECONCILED"));
                        return null;
                    }});
    }

    private boolean admitMarketingReconciliation(AttachmentWorkStore.LeasedJob job) {
        var result = commandTemplate.execute(
                io.github.archetom.crm.application.operation.UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD,
                new ServiceOperation<Boolean>() { @Override public void validate() { }
                    @Override public Boolean execute() {
                        var subject = support.lockSubjectAndAssertLease(job.fence());
                        if (subject == null) return false;
                        boolean publicMarketingCandidate = subject.file().getUploadOrigin()
                                instanceof AttachmentUploadOrigin.PublicMarketingForm;
                        if (publicMarketingCandidate && !subject.attachmentsPermitted()) {
                            // lockSubject has converted the invalid draft into its bounded
                            // deletion/abort workflow. This stale reconciliation must never
                            // issue an external probe that could revive the candidate.
                            support.settleRequired(job.fence(), support.digest("MARKETING_WITHDRAWN"));
                            return false;
                        }
                        return true;
                    }});
        return result.isSuccess() && Boolean.TRUE.equals(result.getData());
    }

    private void retry(AttachmentWorkStore.LeasedJob job) {
        support.retry(job, "RETRY");
    }
}
