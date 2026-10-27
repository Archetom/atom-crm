package io.github.archetom.crm.application.service.attachment.worker;

import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.exception.AttachmentDomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Publishes only inside the target-root lock; it never performs external I/O or uses a human caller. */
@Component
@RequiredArgsConstructor
public class AttachmentPublicationWorker implements AttachmentWorker {
    private final AttachmentWorkerSupport support; private final CommandServiceTemplate commandTemplate;
    private final byte[] owner = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    @Override public void runOnce() {
        for (var job : support.claim(AttachmentWorkStore.JobKind.PUBLICATION, owner)) {
            commandTemplate.execute(io.github.archetom.crm.application.operation.UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD,
                    new ServiceOperation<>() { @Override public void validate() { }
                        @Override public Void execute() {
                            var subject = support.lockSubjectAndAssertLease(job.fence());
                            if (subject == null) return null;
                            if (subject.stagedFileField()) {
                                // B24c candidates stay private until their owning create/update
                                // or OA submit transaction establishes the typed field value.
                                support.settleRequired(job.fence(), support.digest("FIELD_STAGED"));
                                return null;
                            }
                            if (!subject.attachmentsPermitted()) {
                                if (subject.attachmentsPaused()) {
                                    support.deferForPause(job.fence());
                                } else {
                                    support.settleRequired(job.fence(), support.digest("NOT_ELIGIBLE"));
                                }
                                return null;
                            }
                            boolean published = false;
                            if (subject.file().getPublicationState()
                                    == io.github.archetom.crm.domain.model.AttachmentPublicationState.PRIVATE) {
                                try {
                                    subject.file().publish(LocalDateTime.ofInstant(support.now(), ZoneOffset.UTC));
                                    support.fileRepository().save(subject.tenantId(), subject.file());
                                    published = true;
                                } catch (AttachmentDomainException ignored) {
                                    // Withdrawal or an inspection conclusion won the target-root race.
                                }
                            }
                            support.settleRequired(job.fence(), support.digest(published ? "PUBLISHED" : "NOT_ELIGIBLE"));
                            return null;
                        }});
        }
    }
}
