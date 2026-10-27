package io.github.archetom.crm.application.service.attachment.worker;

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

/**
 * Per-file durable retention discovery. Generations are enqueued at intent expiry, quarantine
 * conclusion and withdrawal, so each bounded job discovers only its exact file rather than
 * enumerating a storage prefix or trusting provider listing.
 */
@Component
@RequiredArgsConstructor
public class AttachmentRetentionWorker implements AttachmentWorker {
    private final AttachmentWorkerSupport support;
    private final CommandServiceTemplate commandTemplate;
    private final byte[] owner = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @Override public void runOnce() {
        for (var job : support.claim(AttachmentWorkStore.JobKind.RETENTION_SWEEP, owner)) discover(job);
    }

    private void discover(AttachmentWorkStore.LeasedJob job) {
        commandTemplate.execute(io.github.archetom.crm.application.operation.UseCaseOperation.ATTACHMENT_WITHDRAW,
                new ServiceOperation<Void>() { @Override public void validate() { }
                    @Override public Void execute() {
                        var subject = support.lockSubjectAndAssertLease(job.fence());
                        if (subject == null) return null;
                        var file = subject.file();
                        LocalDateTime now = LocalDateTime.ofInstant(support.now(), ZoneOffset.UTC);
                        AttachmentWorkStore.JobKind cleanup = null;
                        boolean terminalRecovery = job.fence().generation() >= AttachmentWorkStore.TERMINAL_RECOVERY_GENERATION;
                        long cleanupGeneration = terminalRecovery ? job.fence().generation() : 1;
                        if (file.getByteState() == AttachmentByteState.UPLOADING) {
                            // Neither expiry nor a dead reconciliation proves that the exact
                            // locator is absent. A store may have committed immediately before a
                            // finalize crash, so every recovery must probe before aborting.
                            cleanup = AttachmentWorkStore.JobKind.UPLOAD_RECONCILIATION;
                        } else if (terminalRecovery && file.getByteState() == AttachmentByteState.STORED
                                && file.getInspectionState() == AttachmentInspectionState.PENDING
                                && file.getPublicationState() == AttachmentPublicationState.PRIVATE) {
                            // A terminal scanner can never contribute a CLEAN conclusion. Make
                            // the file invisible, then let deletion prove absence before release.
                            file.markInspectionIndeterminate("INSPECTION_TERMINAL", now);
                            file.withdraw(now);
                            if (!subject.stagedFileField()) {
                                support.fileRepository().withdrawRelation(subject.tenantId(), subject.target(), subject.fileId());
                            }
                            support.fileRepository().save(subject.tenantId(), file);
                            support.workStore().adjustUsage(subject.tenantId(), new AttachmentWorkStore.UsageDelta(
                                    0, 0, 0, 0, 0, -1, 0), support.now());
                            cleanup = AttachmentWorkStore.JobKind.DELETION;
                        } else if (terminalRecovery && file.getByteState() == AttachmentByteState.STORED
                                && file.getInspectionState() == AttachmentInspectionState.CLEAN
                                && file.getPublicationState() == AttachmentPublicationState.PRIVATE) {
                            // A terminal publication job must be replayed with a fresh deadline;
                            // otherwise a clean stored object can stay permanently invisible.
                            cleanup = AttachmentWorkStore.JobKind.PUBLICATION;
                        } else if (file.getByteState() == AttachmentByteState.STORED
                                && file.getInspectionState() == AttachmentInspectionState.CLEAN
                                && file.getPublicationState() == AttachmentPublicationState.PRIVATE) {
                            // Expiry is terminal for an unbound clean draft.  It must not be
                            // mistaken for a publication retry: withdraw it so deletion releases
                            // the private bytes and the reserved ready quota.
                            file.withdraw(now);
                            if (!subject.stagedFileField()) {
                                support.fileRepository().withdrawRelation(subject.tenantId(), subject.target(), subject.fileId());
                            }
                            support.fileRepository().save(subject.tenantId(), file);
                            cleanup = AttachmentWorkStore.JobKind.DELETION;
                        } else if ((file.getInspectionState() == AttachmentInspectionState.REJECTED
                                || file.getInspectionState() == AttachmentInspectionState.INDETERMINATE)
                                && file.getPublicationState() == AttachmentPublicationState.PRIVATE) {
                            file.withdraw(now);
                            if (!subject.stagedFileField()) {
                                support.fileRepository().withdrawRelation(subject.tenantId(), subject.target(), subject.fileId());
                            }
                            support.fileRepository().save(subject.tenantId(), file);
                            cleanup = AttachmentWorkStore.JobKind.DELETION;
                        } else if (file.getPublicationState() == AttachmentPublicationState.WITHDRAWN
                                && file.getByteState() != AttachmentByteState.REMOVED) {
                            cleanup = file.getByteState() == AttachmentByteState.REMOVING
                                    ? AttachmentWorkStore.JobKind.DELETION
                                    : AttachmentWorkStore.JobKind.INCOMPLETE_ABORT;
                        }
                        if (cleanup != null) {
                            support.workStore().createJobIfAbsent(new AttachmentWorkStore.JobRequest(
                                    subject.tenantId(), subject.fileId(), file.getContentVersion(), cleanup, cleanupGeneration,
                                    support.now(), support.now().plus(support.policy().tombstoneRetention())));
                        }
                        support.settleRequired(job.fence(), support.digest(cleanup == null ? "RETENTION_CLEAR" : "RETENTION_CLEANUP"));
                        return null;
                    }});
    }
}
