package io.github.archetom.crm.application.service.attachment.worker;

import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.service.impl.CrmImportDraftSourceCleanup;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.OperationLogPolicy;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.CrmImportDraft;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.CrmImportDraftRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.valueobject.CrmImportDraftId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Reclaims expired CSV-import sources without waiting for a caller to revisit the draft. Discovery
 * is bounded and lock-free; every candidate is re-read under the established tenant -> import
 * root -> attachment relation/file order before it can transition or withdraw bytes.
 */
@Component
@RequiredArgsConstructor
public class CrmImportDraftExpiryWorker implements AttachmentWorker {
    private static final int BATCH_SIZE = 25;

    private final CrmImportDraftRepository draftRepository;
    private final AttachmentFileRepository fileRepository;
    private final OrganizationTenantRepository tenantRepository;
    private final CrmImportDraftSourceCleanup sourceCleanup;
    private final AttachmentRuntimePolicy attachmentPolicy;
    private final CommandServiceTemplate commandTemplate;
    private final Clock clock;

    @Override
    public void runOnce() {
        if (!attachmentPolicy.enabled()) return;
        Instant now = clock.instant();
        for (CrmImportDraft candidate : draftRepository.findDueActive(local(now), BATCH_SIZE)) {
            expire(candidate.getTenantId(), candidate.getId(), now);
        }
    }

    private void expire(TenantId tenantId, CrmImportDraftId draftId, Instant now) {
        commandTemplate.execute(UseCaseOperation.CRM_IMPORT_DRAFT_EXPIRE, new ServiceOperation<Void>() {
            @Override public void validate() { }

            @Override public Void execute() {
                if (!tenantRepository.lockForTenantChanges(tenantId)) return null;
                CrmImportDraft draft = draftRepository.findForUpdate(tenantId, draftId).orElse(null);
                if (draft == null || !draft.expire(local(now))) return null;

                AttachmentTarget target = new AttachmentTarget(AttachmentTargetType.IMPORT_DRAFT, draftId.getValue());
                List<AttachmentFile> files = fileRepository.findActiveByTargetForUpdate(tenantId, target, 2);
                if (files.size() > 1) {
                    throw new IllegalStateException("CRM import source is invalid");
                }
                if (!files.isEmpty()) sourceCleanup.withdraw(tenantId, target, files.getFirst(), now);
                draftRepository.save(tenantId, draft);
                return null;
            }
        }, OperationLogPolicy.SAFE_BACKGROUND);
    }

    private static LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC).withNano(value.getNano() / 1_000 * 1_000);
    }
}
