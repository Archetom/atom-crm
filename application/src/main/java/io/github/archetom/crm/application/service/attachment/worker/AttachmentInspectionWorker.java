package io.github.archetom.crm.application.service.attachment.worker;

import io.github.archetom.crm.application.port.out.attachment.AttachmentContentDetector;
import io.github.archetom.crm.application.port.out.attachment.AttachmentContentScanner;
import io.github.archetom.crm.application.port.out.attachment.AttachmentInspectionReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRange;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Drives detector/scanner outside transactions, then fences the normalized conclusion into MySQL. */
@Component
@RequiredArgsConstructor
public class AttachmentInspectionWorker implements AttachmentWorker {
    private final AttachmentWorkerSupport support; private final AttachmentStorage storage;
    private final AttachmentContentDetector detector; private final AttachmentContentScanner scanner;
    private final AttachmentInspectionReadinessResolver readinessResolver;
    private final CommandServiceTemplate commandTemplate;
    private final byte[] owner = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    @Override public void runOnce() {
        for (var job : support.claim(AttachmentWorkStore.JobKind.INSPECTION, owner)) inspect(job);
    }
    private void inspect(AttachmentWorkStore.LeasedJob job) {
        if (!admitInspection(job)) return;
        var plan = support.workStore().findContentPlan(job.fence().tenantId(), job.fence().fileId()).orElse(null);
        if (plan == null || plan.storedIdentity() == null || plan.evidence() == null) { retry(job); return; }
        if (!support.planReady(plan, io.github.archetom.crm.application.port.out.attachment.AttachmentOperation.READ_FULL)) {
            retry(job); return;
        }
        AttachmentContentDetector.Detection detection;
        AttachmentContentScanner.ScanResult scan;
        try (var lease = support.heartbeatGuard(job)) {
            if (!lease.isCurrent()) return;
            try (var detectorHandle = storage.open(new AttachmentStorage.OpenCommand(plan.binding(),
                    plan.storedIdentity(), plan.evidence(), new AttachmentRange(0, plan.evidence().actualBytes(),
                    plan.evidence().actualBytes()), job.deadline()))) {
                detection = detector.detect(detectorHandle, plan.evidence());
                if (!lease.isCurrent()) return;
            }
        } catch (Exception exception) { retry(job); return; }
        try (var lease = support.heartbeatGuard(job)) {
            if (!lease.isCurrent()) return;
            try (var scannerHandle = storage.open(new AttachmentStorage.OpenCommand(plan.binding(),
                    plan.storedIdentity(), plan.evidence(), new AttachmentRange(0, plan.evidence().actualBytes(),
                    plan.evidence().actualBytes()), job.deadline()))) {
                scan = scanner.scan(scannerHandle, plan.evidence());
                if (!lease.isCurrent()) return;
            }
        } catch (Exception exception) { retry(job); return; }
        commandTemplate.execute(io.github.archetom.crm.application.operation.UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD,
                new ServiceOperation<>() { @Override public void validate() { }
                    @Override public Void execute() {
                        var subject = support.lockSubjectAndAssertLease(job.fence());
                        if (subject == null) return null;
                        LocalDateTime now = LocalDateTime.ofInstant(support.now(), ZoneOffset.UTC);
                        if (pausedOrWithdrawn(subject, job, now)) return null;
                        if (subject.file().getByteState() != io.github.archetom.crm.domain.model.AttachmentByteState.STORED) {
                            releaseObsoleteInspection(subject, now);
                            support.settleRequired(job.fence(), support.digest("INSPECTION_OBSOLETE"));
                            return null;
                        }
                        // Resolve readiness under the target/file/job transaction, immediately
                        // before accepting the scan. This is the commit-time generation fence.
                        Outcome outcome = outcome(detection, scan, plan);
                        switch (outcome) {
                            case CLEAN -> {
                                if (!detection.mediaType().equals(subject.file().getDeclaredMediaType())) {
                                    subject.file().markInspectionRejected("TYPE_MISMATCH", now);
                                    support.workStore().adjustUsage(subject.tenantId(), new AttachmentWorkStore.UsageDelta(
                                            0, 0, 0, 0, 0, -1, 0), support.now());
                                    scheduleQuarantineRetention(subject, job);
                                } else {
                                    subject.file().markInspectionClean(now);
                                    support.workStore().adjustUsage(subject.tenantId(), new AttachmentWorkStore.UsageDelta(
                                            subject.file().getActualBytes(), -subject.file().getActualBytes(),
                                            0, 0, 0, -1, 0), support.now());
                                    support.workStore().createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.tenantId(),
                                            subject.fileId(), subject.file().getContentVersion(),
                                            AttachmentWorkStore.JobKind.PUBLICATION, 1, support.now(),
                                            support.now().plus(support.policy().tombstoneRetention())));
                                }
                            }
                            case REJECTED -> {
                                subject.file().markInspectionRejected("POLICY_REJECTED", now);
                                support.workStore().adjustUsage(subject.tenantId(), new AttachmentWorkStore.UsageDelta(
                                        0, 0, 0, 0, 0, -1, 0), support.now());
                                scheduleQuarantineRetention(subject, job);
                            }
                            case INDETERMINATE -> {
                                subject.file().markInspectionIndeterminate("INSPECTION_INDETERMINATE", now);
                                support.workStore().adjustUsage(subject.tenantId(), new AttachmentWorkStore.UsageDelta(
                                        0, 0, 0, 0, 0, -1, 0), support.now());
                                scheduleQuarantineRetention(subject, job);
                            }
                        }
                        support.fileRepository().save(subject.tenantId(), subject.file());
                        support.settleRequired(job.fence(), support.digest(outcome.name()));
                        return null;
                    }});
    }
    /**
     * The inspection slot is reserved when STORED is finalized. Before touching storage, fence
     * and target state are rechecked under the target root so a withdrawal/deletion cannot leave
     * an expired scan repeatedly reopening an already removed object.
     */
    private boolean admitInspection(AttachmentWorkStore.LeasedJob job) {
        var result = commandTemplate.execute(io.github.archetom.crm.application.operation.UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD,
                new ServiceOperation<Boolean>() { @Override public void validate() { }
                    @Override public Boolean execute() {
                        var subject = support.lockSubjectAndAssertLease(job.fence());
                        if (subject == null) return false;
                        if (pausedOrWithdrawn(subject, job,
                                LocalDateTime.ofInstant(support.now(), ZoneOffset.UTC))) return false;
                        if (subject.file().getByteState()
                                != io.github.archetom.crm.domain.model.AttachmentByteState.STORED) {
                            releaseObsoleteInspection(subject, LocalDateTime.ofInstant(support.now(), ZoneOffset.UTC));
                            support.settleRequired(job.fence(), support.digest("INSPECTION_OBSOLETE"));
                            return false;
                        }
                        return true;
                    }});
        return result.isSuccess() && Boolean.TRUE.equals(result.getData());
    }
    private Outcome outcome(AttachmentContentDetector.Detection detection,
                            AttachmentContentScanner.ScanResult scan, AttachmentWorkStore.ContentPlan plan) {
        // Scanner threat is the strongest evidence and must win even when detection crashed,
        // returned null or is otherwise unusable.
        if (scan != null && scan.verdict() == AttachmentContentScanner.ScanVerdict.THREAT_DETECTED) return Outcome.REJECTED;
        if (scan == null || detection == null) return Outcome.INDETERMINATE;
        var readiness = readinessResolver.resolve(plan.tenantId());
        if (readiness == null || detection.state() != AttachmentContentDetector.DetectionState.IDENTIFIED
                || detection.evidenceLevel() == null || scan.definitionBasis() == null
                || scan.definitionGeneration() == null || readiness.definitionGeneration() == null
                || !scan.definitionGeneration().equals(readiness.definitionGeneration())
                || !readiness.allows(detection.mediaType(), support.now())
                || scan.verdict() != AttachmentContentScanner.ScanVerdict.NO_THREAT_DETECTED) return Outcome.INDETERMINATE;
        // Media declaration was frozen at intent time; worker never accepts a detector disagreement.
        return Outcome.CLEAN;
    }
    private void retry(AttachmentWorkStore.LeasedJob job) {
        support.retry(job, "RETRY");
    }
    private void scheduleQuarantineRetention(AttachmentWorkerSupport.Subject subject,
                                             AttachmentWorkStore.LeasedJob job) {
        var notBefore = support.now().plus(support.policy().quarantineRetention());
        support.workStore().createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.tenantId(),
                subject.fileId(), subject.file().getContentVersion(), AttachmentWorkStore.JobKind.RETENTION_SWEEP,
                2, notBefore, notBefore.plus(support.policy().tombstoneRetention())));
    }
    private void releaseObsoleteInspection(AttachmentWorkerSupport.Subject subject, LocalDateTime now) {
        if (!subject.file().abandonPendingInspection("INSPECTION_OBSOLETE", now)) return;
        support.fileRepository().save(subject.tenantId(), subject.file());
        support.workStore().adjustUsage(subject.tenantId(), new AttachmentWorkStore.UsageDelta(
                0, 0, 0, 0, 0, -1, 0), support.now());
    }

    /** A pool pause retains inspection work; a withdrawn parent releases it to cleanup. */
    private boolean pausedOrWithdrawn(AttachmentWorkerSupport.Subject subject,
                                      AttachmentWorkStore.LeasedJob job, LocalDateTime now) {
        if (subject.attachmentsPermitted()) return false;
        if (subject.attachmentsPaused()) {
            support.deferForPause(job.fence());
        } else {
            releaseObsoleteInspection(subject, now);
            support.settleRequired(job.fence(), support.digest("INSPECTION_WITHDRAWN"));
        }
        return true;
    }
    private enum Outcome { CLEAN, REJECTED, INDETERMINATE }
}
