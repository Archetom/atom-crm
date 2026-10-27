package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.OaRequestAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.entity.OaRequest;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ApprovalInstanceRepository;
import io.github.archetom.crm.domain.repository.OaRequestRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OaRequestAttachmentTargetAccessTest {
    private static final TenantId TENANT = new TenantId(1L);
    private static final MemberId APPLICANT = new MemberId(2L);
    private static final ApprovalInstanceId APPROVAL_ID = new ApprovalInstanceId(8L);
    private static final OaRequestId REQUEST_ID = new OaRequestId(9L);
    private static final AttachmentTarget TARGET =
            new AttachmentTarget(AttachmentTargetType.OA_REQUEST, REQUEST_ID.getValue());

    @Test
    void permitsApplicantOnlyWhilePendingAndLocksApprovalBeforeRequest() {
        OrganizationAccessGuard guard = mock(OrganizationAccessGuard.class);
        OaRequestAccessResolver accessResolver = mock(OaRequestAccessResolver.class);
        OrganizationTenantRepository tenants = mock(OrganizationTenantRepository.class);
        OaRequestRepository requests = mock(OaRequestRepository.class);
        ApprovalInstanceRepository approvals = mock(ApprovalInstanceRepository.class);
        AuthenticatedCaller caller = caller(APPLICANT, false);
        OaRequest request = request();
        ApprovalInstance instance = pending(true);
        OaRequestAccessResolver.OaRequestAccess access = access(APPLICANT, false);
        when(guard.requireTenant(caller, AuthorityCatalog.OA_REQUESTS_COLLABORATE))
                .thenReturn(TENANT);
        when(tenants.lockForTenantChanges(TENANT)).thenReturn(true);
        when(guard.requireActiveMembershipForShare(caller,
                AuthorityCatalog.OA_REQUESTS_COLLABORATE)).thenReturn(TENANT);
        when(accessResolver.accessForShare(caller)).thenReturn(access);
        when(requests.findVisibleById(TENANT, REQUEST_ID, APPLICANT, false))
                .thenReturn(Optional.of(request));
        when(approvals.findByIdForUpdate(TENANT, APPROVAL_ID)).thenReturn(Optional.of(instance));
        when(requests.findByIdForUpdate(TENANT, REQUEST_ID)).thenReturn(Optional.of(request));

        OaRequestAttachmentTargetAccess targetAccess = new OaRequestAttachmentTargetAccess(
                guard, accessResolver, tenants, requests, approvals);

        assertTrue(targetAccess.lockForAttachmentMutation(caller, TARGET,
                AttachmentTargetOperation.CREATE_INTENT).attachmentsPermitted());
        InOrder tenantThenMembership = inOrder(guard, tenants, accessResolver);
        tenantThenMembership.verify(guard).requireTenant(caller,
                AuthorityCatalog.OA_REQUESTS_COLLABORATE);
        tenantThenMembership.verify(tenants).lockForTenantChanges(TENANT);
        tenantThenMembership.verify(guard).requireActiveMembershipForShare(caller,
                AuthorityCatalog.OA_REQUESTS_COLLABORATE);
        tenantThenMembership.verify(accessResolver).accessForShare(caller);
        verify(guard, never()).requireActiveMembership(caller,
                AuthorityCatalog.OA_REQUESTS_COLLABORATE);
        InOrder order = inOrder(approvals, requests);
        order.verify(approvals).findByIdForUpdate(TENANT, APPROVAL_ID);
        order.verify(requests).findByIdForUpdate(TENANT, REQUEST_ID);
    }

    @Test
    void rejectsHistoricalAssigneeWatcherAndTenantOaManagerForCollaboration() {
        OrganizationAccessGuard guard = mock(OrganizationAccessGuard.class);
        OaRequestAccessResolver accessResolver = mock(OaRequestAccessResolver.class);
        OrganizationTenantRepository tenants = mock(OrganizationTenantRepository.class);
        OaRequestRepository requests = mock(OaRequestRepository.class);
        ApprovalInstanceRepository approvals = mock(ApprovalInstanceRepository.class);
        OaRequest request = request();
        ApprovalInstance instance = pending(false);
        OaRequestAttachmentTargetAccess targetAccess = new OaRequestAttachmentTargetAccess(
                guard, accessResolver, tenants, requests, approvals);

        for (MemberId member : Set.of(new MemberId(3L), new MemberId(4L), new MemberId(5L))) {
            AuthenticatedCaller caller = caller(member, true);
            OaRequestAccessResolver.OaRequestAccess access = access(member, true);
            when(guard.requireActiveMembership(caller, AuthorityCatalog.OA_REQUESTS_COLLABORATE))
                    .thenReturn(TENANT);
            when(accessResolver.access(caller)).thenReturn(access);
            when(requests.findVisibleById(TENANT, REQUEST_ID, member, true))
                    .thenReturn(Optional.of(request));
            when(approvals.findById(TENANT, APPROVAL_ID)).thenReturn(Optional.of(instance));

            assertThrows(RuntimeException.class,
                    () -> targetAccess.authorizeCollaborate(caller, TARGET));
        }
    }

    @Test
    void keepsAvailableFilesReadableAfterApprovalTerminalButPreventsWorkersFromPublishing() {
        OrganizationAccessGuard guard = mock(OrganizationAccessGuard.class);
        OaRequestAccessResolver accessResolver = mock(OaRequestAccessResolver.class);
        OrganizationTenantRepository tenants = mock(OrganizationTenantRepository.class);
        OaRequestRepository requests = mock(OaRequestRepository.class);
        ApprovalInstanceRepository approvals = mock(ApprovalInstanceRepository.class);
        AuthenticatedCaller caller = caller(APPLICANT, false);
        OaRequest request = request();
        ApprovalInstance terminal = mock(ApprovalInstance.class);
        when(terminal.getStatus()).thenReturn(ApprovalInstanceStatus.APPROVED);
        OaRequestAccessResolver.OaRequestAccess access = access(APPLICANT, false);
        when(accessResolver.access(caller)).thenReturn(access);
        when(accessResolver.tenant(caller)).thenReturn(TENANT);
        when(accessResolver.accessForShare(caller)).thenReturn(access);
        when(tenants.lockForTenantChanges(TENANT)).thenReturn(true);
        when(requests.findVisibleById(TENANT, REQUEST_ID, APPLICANT, false))
                .thenReturn(Optional.of(request));
        when(requests.findById(TENANT, REQUEST_ID)).thenReturn(Optional.of(request));
        when(approvals.findByIdForUpdate(TENANT, APPROVAL_ID)).thenReturn(Optional.of(terminal));
        when(requests.findByIdForUpdate(TENANT, REQUEST_ID)).thenReturn(Optional.of(request));
        OaRequestAttachmentTargetAccess targetAccess = new OaRequestAttachmentTargetAccess(
                guard, accessResolver, tenants, requests, approvals);

        assertSame(TENANT, targetAccess.authorizeRead(caller, TARGET));
        assertTrue(targetAccess.lockForAttachmentMutation(caller, TARGET,
                AttachmentTargetOperation.DOWNLOAD).attachmentsPermitted());
        assertTrue(!targetAccess.lockForWorker(TENANT, TARGET).attachmentsPermitted());
    }

    private static AuthenticatedCaller caller(MemberId member, boolean manager) {
        Set<String> authorities = manager
                ? Set.of(AuthorityCatalog.OA_REQUESTS_READ, AuthorityCatalog.OA_REQUESTS_COLLABORATE,
                AuthorityCatalog.OA_REQUESTS_MANAGE)
                : Set.of(AuthorityCatalog.OA_REQUESTS_READ, AuthorityCatalog.OA_REQUESTS_COLLABORATE);
        return new AuthenticatedCaller(member.getValue(), TENANT.getValue(), authorities);
    }

    private static OaRequestAccessResolver.OaRequestAccess access(MemberId member, boolean manager) {
        return new OaRequestAccessResolver.OaRequestAccess(TENANT, member, manager);
    }

    private static OaRequest request() {
        OaRequest value = mock(OaRequest.class);
        when(value.getApprovalInstanceId()).thenReturn(APPROVAL_ID);
        return value;
    }

    private static ApprovalInstance pending(boolean currentCollaborator) {
        ApprovalInstance value = mock(ApprovalInstance.class);
        when(value.getStatus()).thenReturn(ApprovalInstanceStatus.PENDING);
        when(value.isCurrentOaAttachmentCollaborator(org.mockito.ArgumentMatchers.any()))
                .thenReturn(currentCollaborator);
        return value;
    }
}
