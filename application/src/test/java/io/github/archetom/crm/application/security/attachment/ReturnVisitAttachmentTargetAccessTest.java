package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.ReturnVisitAccessResolver;
import io.github.archetom.crm.domain.entity.ReturnVisit;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.ReturnVisitAccessMode;
import io.github.archetom.crm.domain.model.ReturnVisitStatus;
import io.github.archetom.crm.domain.model.ReturnVisitVisibility;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.ReturnVisitRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReturnVisitId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReturnVisitAttachmentTargetAccessTest {
    private static final TenantId TENANT = new TenantId(1L);
    private static final MemberId MEMBER = new MemberId(2L);
    private static final AttachmentTarget TARGET = new AttachmentTarget(AttachmentTargetType.RETURN_VISIT, 9L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(2L, 1L, Set.of());

    @Test
    void reusesReadAndWriteScopeSeparatelyForFileOperations() {
        ReturnVisitAccessResolver resolver = mock(ReturnVisitAccessResolver.class);
        OrganizationTenantRepository tenantRepository = mock(OrganizationTenantRepository.class);
        ReturnVisitRepository visits = mock(ReturnVisitRepository.class);
        ReturnVisit value = mock(ReturnVisit.class);
        ReturnVisitVisibility readVisibility = mock(ReturnVisitVisibility.class);
        ReturnVisitVisibility writeVisibility = mock(ReturnVisitVisibility.class);
        when(resolver.access(CALLER, AuthorityCatalog.RETURN_VISITS_READ, ReturnVisitAccessMode.READ))
                .thenReturn(new ReturnVisitAccessResolver.ReturnVisitAccess(TENANT, MEMBER, readVisibility));
        when(resolver.access(CALLER, AuthorityCatalog.RETURN_VISITS_WRITE, ReturnVisitAccessMode.WRITE))
                .thenReturn(new ReturnVisitAccessResolver.ReturnVisitAccess(TENANT, MEMBER, writeVisibility));
        when(visits.findVisibleById(eq(TENANT), any(ReturnVisitId.class), eq(readVisibility)))
                .thenReturn(Optional.of(value));
        when(tenantRepository.lockForTenantChanges(TENANT)).thenReturn(true);
        when(visits.findVisibleByIdForUpdate(eq(TENANT), any(ReturnVisitId.class), eq(writeVisibility)))
                .thenReturn(Optional.of(value));
        when(value.getStatus()).thenReturn(ReturnVisitStatus.ACTIVE);

        ReturnVisitAttachmentTargetAccess access = new ReturnVisitAttachmentTargetAccess(
                resolver, tenantRepository, visits);

        assertSame(TENANT, access.authorizeRead(CALLER, TARGET));
        assertTrue(access.lockForAttachmentMutation(CALLER, TARGET,
                AttachmentTargetOperation.CREATE_INTENT).attachmentsPermitted());
        verify(resolver).access(CALLER, AuthorityCatalog.RETURN_VISITS_READ, ReturnVisitAccessMode.READ);
        verify(resolver).access(CALLER, AuthorityCatalog.RETURN_VISITS_WRITE, ReturnVisitAccessMode.WRITE);
    }
}
