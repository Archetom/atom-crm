package io.github.archetom.crm.application.service.attachment;

import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.entity.OaRequest;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.repository.OaRequestRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Keeps the OA terminal transition and collaboration attachment cutoff in one transaction.
 * The caller has already locked the exact approval instance; this class only advances the
 * required order with OA request -> relation/file locks before the instance is persisted.
 */
@Component
@RequiredArgsConstructor
public class OaRequestAttachmentTerminalCoordinator {
    private final OaRequestRepository requestRepository;
    private final AttachmentParentLifecycle attachmentLifecycle;

    public void cutoffIfTerminal(TenantId tenantId, ApprovalInstance instance) {
        if (tenantId == null || instance == null
                || instance.getObjectType() != BusinessObjectType.OA_REQUEST
                || instance.getStatus() == ApprovalInstanceStatus.PENDING) {
            return;
        }
        OaRequest request = requestRepository.findByApprovalInstanceForUpdate(tenantId, instance.getId())
                .orElseThrow(() -> new IllegalStateException("OA request approval association is missing"));
        if (!instance.getId().equals(request.getApprovalInstanceId())) {
            throw new IllegalStateException("OA request approval association is invalid");
        }
        attachmentLifecycle.cutoffNonAvailable(tenantId,
                new AttachmentTarget(AttachmentTargetType.OA_REQUEST, request.getId().getValue()));
    }
}
