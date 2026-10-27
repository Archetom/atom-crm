package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.model.AttachmentByteState;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Withdraws the one private source file attached to an import root and hands byte reclamation to
 * the existing fenced attachment workers. It intentionally runs inside the caller's root-lock
 * transaction; this class neither opens source bytes nor performs storage I/O itself.
 */
@Component
@RequiredArgsConstructor
public class CrmImportDraftSourceCleanup {
    private final AttachmentFileRepository fileRepository;
    private final AttachmentWorkStore workStore;
    private final AttachmentRuntimePolicy attachmentPolicy;

    public void withdraw(TenantId tenantId, AttachmentTarget target, AttachmentFile file, Instant now) {
        if (tenantId == null || target == null || file == null || now == null) {
            throw new IllegalArgumentException("CRM import source cleanup is invalid");
        }
        file.withdraw(local(now));
        fileRepository.withdrawRelation(tenantId, target, file.getId());
        fileRepository.save(tenantId, file);
        AttachmentWorkStore.JobKind kind = file.getByteState() == AttachmentByteState.REMOVING
                ? AttachmentWorkStore.JobKind.DELETION : AttachmentWorkStore.JobKind.INCOMPLETE_ABORT;
        Instant deadline = now.plus(attachmentPolicy.tombstoneRetention());
        workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, file.getId(),
                file.getContentVersion(), kind, 1, now, deadline));
        workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, file.getId(),
                file.getContentVersion(), AttachmentWorkStore.JobKind.RETENTION_SWEEP, 3, deadline,
                deadline.plus(attachmentPolicy.tombstoneRetention())));
    }

    private static LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC).withNano(value.getNano() / 1_000 * 1_000);
    }
}
