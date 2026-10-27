package io.github.archetom.crm.application.service.attachment.worker;

import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.model.AttachmentByteState;
import io.github.archetom.crm.domain.model.AttachmentInspectionState;
import io.github.archetom.crm.domain.model.AttachmentPublicationState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AttachmentIncompleteAbortWorker implements AttachmentWorker {
    private final AttachmentWorkerSupport support; private final AttachmentStorage storage;
    private final CommandServiceTemplate commandTemplate;
    private final byte[] owner = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    @Override public void runOnce() {
        for (var job : support.claim(AttachmentWorkStore.JobKind.INCOMPLETE_ABORT, owner)) {
            if (!support.leaseMayBeCurrent(job.fence()) || !support.heartbeat(job)) continue;
            var plan = support.workStore().findContentPlan(job.fence().tenantId(), job.fence().fileId()).orElse(null);
            try {
                if (plan != null && (!support.planReady(plan,
                        io.github.archetom.crm.application.port.out.attachment.AttachmentOperation.ABORT_INCOMPLETE)
                        || !support.planReady(plan,
                        io.github.archetom.crm.application.port.out.attachment.AttachmentOperation.PROBE_EXACT))) {
                    support.retry(job, "BINDING_NOT_READY");
                    continue;
                }
                if (plan != null) {
                    if (plan.storedIdentity() != null && plan.evidence() != null) {
                        routeKnownStoreToExactDeletion(job, plan.storedIdentity(), plan.evidence());
                        continue;
                    }
                    AttachmentStorage.ProbeResult probe;
                    try (var lease = support.heartbeatGuard(job)) {
                        if (!lease.isCurrent()) return;
                        probe = storage.probe(new AttachmentStorage.ProbeCommand(plan.binding(), plan.locator(),
                                null, plan.storageGeneration(), job.deadline()));
                        if (!lease.isCurrent()) return;
                    }
                    if (probe == null || probe.state() == AttachmentStorage.ProbeState.UNKNOWN) {
                        support.retry(job, "PROBE_UNKNOWN");
                        continue;
                    }
                    if (probe.state() == AttachmentStorage.ProbeState.PRESENT && probe.identity() != null
                            && probe.evidence() != null) {
                        routeKnownStoreToExactDeletion(job, probe.identity(), probe.evidence());
                        continue;
                    }
                    // MISMATCH is positive evidence that this locator is not the planned
                    // attempt (for example, a foreign generation is present).  abortIncomplete
                    // deliberately refuses to delete that object, so settling this job as if an
                    // absence had been proved would orphan the object and release its quota.
                    // Only an authoritative ABSENT probe may take the best-effort abort path.
                    if (probe.state() != AttachmentStorage.ProbeState.ABSENT) {
                        support.retry(job, "PROBE_MISMATCH");
                        continue;
                    }
                    AttachmentStorage.AbortState abortState;
                    try (var lease = support.heartbeatGuard(job)) {
                        if (!lease.isCurrent()) return;
                        abortState = storage.abortIncomplete(plan.binding(), plan.locator(), plan.storageGeneration(), plan.attempt(),
                                job.deadline());
                        if (!lease.isCurrent()) return;
                    }
                    // Probe and abort are separate storage operations.  A descriptor from a
                    // foreign allocation can appear between them, so only a post-delete,
                    // crash-stable ABSENT result authorizes quota release and REMOVED.
                    if (abortState != AttachmentStorage.AbortState.ABSENT) {
                        support.retry(job, "ABORT_" + abortState);
                        continue;
                    }
                }
                commandTemplate.execute(io.github.archetom.crm.application.operation.UseCaseOperation.ATTACHMENT_WITHDRAW,
                        new ServiceOperation<Void>() { @Override public void validate() { }
                            @Override public Void execute() {
                                var subject = support.lockSubjectAndAssertLease(job.fence());
                                if (subject == null) return null;
                                if (subject.file().getByteState() != AttachmentByteState.REMOVED) {
                                    if (subject.file().getPublicationState() != AttachmentPublicationState.WITHDRAWN
                                            && subject.file().getPublicationState() != AttachmentPublicationState.DELETED) {
                                        subject.file().withdraw(LocalDateTime.ofInstant(support.now(), ZoneOffset.UTC));
                                        if (!subject.stagedFileField()) {
                                            support.fileRepository().withdrawRelation(subject.tenantId(), subject.target(), subject.fileId());
                                        }
                                    }
                                    if (subject.file().getActualBytes() == 0) {
                                        support.workStore().adjustUsage(subject.tenantId(), new AttachmentWorkStore.UsageDelta(
                                                0, 0, -subject.file().getExpectedBytes(), -1, -1, 0, 0), support.now());
                                    } else {
                                        long bytes = subject.file().getActualBytes();
                                        boolean clean = subject.file().getInspectionState()
                                                == AttachmentInspectionState.CLEAN;
                                        boolean pendingInspection = subject.file().getInspectionState()
                                                == AttachmentInspectionState.PENDING;
                                        support.workStore().adjustUsage(subject.tenantId(), new AttachmentWorkStore.UsageDelta(
                                                clean ? -bytes : 0, clean ? 0 : -bytes, 0, 0, 0,
                                                pendingInspection ? -1 : 0, 0), support.now());
                                    }
                                    subject.file().markRemoved(LocalDateTime.ofInstant(support.now(), ZoneOffset.UTC));
                                    support.fileRepository().save(subject.tenantId(), subject.file());
                                }
                                support.settleRequired(job.fence(), support.digest("ABORTED"));
                                return null;
                            }});
            } catch (Exception exception) {
                support.retry(job, "RETRY");
            }
        }
    }

    /** A complete descriptor is never eligible for the best-effort abort path. */
    private void routeKnownStoreToExactDeletion(AttachmentWorkStore.LeasedJob job,
                                                 io.github.archetom.crm.application.port.out.attachment.StoredObjectIdentity identity,
                                                 io.github.archetom.crm.domain.model.AttachmentContentEvidence evidence) {
        commandTemplate.execute(io.github.archetom.crm.application.operation.UseCaseOperation.ATTACHMENT_WITHDRAW,
                new ServiceOperation<Void>() { @Override public void validate() { }
                    @Override public Void execute() {
                        var subject = support.lockSubjectAndAssertLease(job.fence());
                        if (subject == null) return null;
                        var plan = support.workStore().findContentPlan(subject.tenantId(), subject.fileId()).orElseThrow();
                        if (plan.storedIdentity() == null) {
                            boolean captured = subject.file().captureStoredForRemoval(evidence,
                                    subject.file().getDeclaredMediaType(),
                                    LocalDateTime.ofInstant(support.now(), ZoneOffset.UTC));
                            support.workStore().recordStored(subject.tenantId(), subject.fileId(),
                                    subject.file().getContentVersion(), plan.attempt(), identity, evidence, support.now());
                            if (captured) {
                                support.workStore().adjustUsage(subject.tenantId(), new AttachmentWorkStore.UsageDelta(
                                        0, evidence.actualBytes(), -subject.file().getExpectedBytes(), -1, -1, 0, 0),
                                        support.now());
                                support.fileRepository().save(subject.tenantId(), subject.file());
                            }
                        }
                        support.workStore().createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.tenantId(),
                                subject.fileId(), subject.file().getContentVersion(),
                                AttachmentWorkStore.JobKind.DELETION, 2, support.now(),
                                support.now().plus(support.policy().tombstoneRetention())));
                        support.settleRequired(job.fence(), support.digest("STORED_TO_DELETE"));
                        return null;
                    }});
    }
}
