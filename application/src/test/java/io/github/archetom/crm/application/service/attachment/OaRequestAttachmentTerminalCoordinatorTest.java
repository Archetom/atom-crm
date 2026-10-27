package io.github.archetom.crm.application.service.attachment;

import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.entity.OaRequest;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.repository.OaRequestRepository;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OaRequestAttachmentTerminalCoordinatorTest {
    private static final TenantId TENANT = new TenantId(1L);
    private static final ApprovalInstanceId APPROVAL_ID = new ApprovalInstanceId(7L);
    private static final OaRequestId REQUEST_ID = new OaRequestId(9L);

    @Test
    void closesOnlyNonAvailableCandidatesBeforePersistingAnOaTerminalApproval() {
        OaRequestRepository requests = mock(OaRequestRepository.class);
        AttachmentParentLifecycle lifecycle = mock(AttachmentParentLifecycle.class);
        ApprovalInstance approval = mock(ApprovalInstance.class);
        OaRequest request = mock(OaRequest.class);
        when(approval.getObjectType()).thenReturn(BusinessObjectType.OA_REQUEST);
        when(approval.getStatus()).thenReturn(ApprovalInstanceStatus.REJECTED);
        when(approval.getId()).thenReturn(APPROVAL_ID);
        when(request.getApprovalInstanceId()).thenReturn(APPROVAL_ID);
        when(request.getId()).thenReturn(REQUEST_ID);
        when(requests.findByApprovalInstanceForUpdate(TENANT, APPROVAL_ID))
                .thenReturn(Optional.of(request));

        new OaRequestAttachmentTerminalCoordinator(requests, lifecycle)
                .cutoffIfTerminal(TENANT, approval);

        verify(lifecycle).cutoffNonAvailable(TENANT,
                new AttachmentTarget(AttachmentTargetType.OA_REQUEST, REQUEST_ID.getValue()));
    }

    @Test
    void leavesPendingApprovalsUntouched() {
        OaRequestRepository requests = mock(OaRequestRepository.class);
        AttachmentParentLifecycle lifecycle = mock(AttachmentParentLifecycle.class);
        ApprovalInstance approval = mock(ApprovalInstance.class);
        when(approval.getObjectType()).thenReturn(BusinessObjectType.OA_REQUEST);
        when(approval.getStatus()).thenReturn(ApprovalInstanceStatus.PENDING);

        new OaRequestAttachmentTerminalCoordinator(requests, lifecycle)
                .cutoffIfTerminal(TENANT, approval);

        verify(requests, never()).findByApprovalInstanceForUpdate(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(lifecycle, never()).cutoffNonAvailable(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
