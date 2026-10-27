package io.github.archetom.crm.application.service.attachment.worker;

import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.model.AttachmentByteState;
import io.github.archetom.crm.domain.model.AttachmentInspectionState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Separates delete effect from authoritative absence confirmation; only the latter writes REMOVED. */
@Component
@RequiredArgsConstructor
public class AttachmentDeletionWorker implements AttachmentWorker {
    private final AttachmentWorkerSupport support; private final AttachmentStorage storage;
    private final CommandServiceTemplate commandTemplate;
    private final byte[] owner = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    @Override public void runOnce() {
        for (var job : support.claim(AttachmentWorkStore.JobKind.DELETION, owner)) delete(job);
    }
    private void delete(AttachmentWorkStore.LeasedJob job) {
        if (!deletionAllowed(job)) { retry(job); return; }
        var plan = support.workStore().findContentPlan(job.fence().tenantId(), job.fence().fileId()).orElse(null);
        if (plan == null || plan.storedIdentity() == null || plan.evidence() == null) { settleRemoved(job); return; }
        if (!support.planReady(plan, io.github.archetom.crm.application.port.out.attachment.AttachmentOperation.DELETE_EXACT)
                || !support.planReady(plan, io.github.archetom.crm.application.port.out.attachment.AttachmentOperation.CONFIRM_ABSENCE)) {
            retry(job); return;
        }
        try {
            var command = new AttachmentStorage.DeleteCommand(plan.binding(), plan.storedIdentity(),
                    plan.evidence(), job.deadline());
            if (!job.effectRecorded()) {
                try (var lease = support.heartbeatGuard(job)) {
                    if (!lease.isCurrent()) return;
                    storage.requestDelete(command);
                    if (!lease.isCurrent()) return;
                }
                if (!support.workStore().recordJobEffect(job.fence(), support.digest("DELETE_EFFECT"), support.now())) {
                    return;
                }
            }
            boolean absent;
            try (var lease = support.heartbeatGuard(job)) {
                if (!lease.isCurrent()) return;
                absent = storage.confirmAbsent(command);
                if (!lease.isCurrent()) return;
            }
            if (!absent) { retry(job); return; }
        } catch (Exception exception) { retry(job); return; }
        settleRemoved(job);
    }
    private void settleRemoved(AttachmentWorkStore.LeasedJob job) {
        commandTemplate.execute(io.github.archetom.crm.application.operation.UseCaseOperation.ATTACHMENT_WITHDRAW,
                new ServiceOperation<>() { @Override public void validate() { }
                    @Override public Void execute() {
                        var subject = support.lockSubjectAndAssertLease(job.fence());
                        if (subject == null) return null;
                        if (support.workStore().hasActiveReadLease(subject.tenantId(), subject.fileId(),
                                subject.file().getContentVersion(), support.now())) {
                            return null;
                        }
                        if (subject.file().getByteState() != AttachmentByteState.REMOVED) {
                            long bytes = subject.file().getActualBytes();
                            if (bytes > 0) {
                                boolean clean = subject.file().getInspectionState() == AttachmentInspectionState.CLEAN;
                                boolean pendingInspection = subject.file().getInspectionState()
                                        == AttachmentInspectionState.PENDING;
                                support.workStore().adjustUsage(subject.tenantId(), new AttachmentWorkStore.UsageDelta(
                                        clean ? -bytes : 0, clean ? 0 : -bytes,
                                        0, 0, 0, pendingInspection ? -1 : 0, 0), support.now());
                            }
                            subject.file().markRemoved(LocalDateTime.ofInstant(support.now(), ZoneOffset.UTC));
                            support.fileRepository().save(subject.tenantId(), subject.file());
                        }
                        support.settleRequired(job.fence(), support.digest("REMOVED"));
                        return null;
                    }});
    }
    private boolean deletionAllowed(AttachmentWorkStore.LeasedJob job) {
        var result = commandTemplate.execute(io.github.archetom.crm.application.operation.UseCaseOperation.ATTACHMENT_WITHDRAW,
                new ServiceOperation<Boolean>() { @Override public void validate() { }
                    @Override public Boolean execute() {
                        var subject = support.lockSubjectAndAssertLease(job.fence());
                        if (subject == null) return false;
                        return !support.workStore().hasActiveReadLease(subject.tenantId(), subject.fileId(),
                                subject.file().getContentVersion(), support.now());
                    }});
        return result.isSuccess() && Boolean.TRUE.equals(result.getData());
    }
    private void retry(AttachmentWorkStore.LeasedJob job) {
        support.retry(job, "RETRY");
    }
}
