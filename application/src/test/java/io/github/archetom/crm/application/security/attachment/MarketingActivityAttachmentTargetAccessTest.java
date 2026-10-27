package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.domain.entity.MarketingActivity;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.MarketingActivityRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.valueobject.MarketingActivityId;
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

class MarketingActivityAttachmentTargetAccessTest {
    private static final TenantId TENANT = new TenantId(1L);
    private static final AttachmentTarget TARGET =
            new AttachmentTarget(AttachmentTargetType.MARKETING_ACTIVITY, 9L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(2L, 1L, Set.of());

    @Test
    void intersectsActivityAuthorityWithAnActiveTenantRoot() {
        OrganizationAccessGuard guard = mock(OrganizationAccessGuard.class);
        OrganizationTenantRepository tenantRepository = mock(OrganizationTenantRepository.class);
        MarketingActivityRepository activities = mock(MarketingActivityRepository.class);
        MarketingActivity activity = mock(MarketingActivity.class);
        when(guard.requireActiveMembership(CALLER, AuthorityCatalog.MARKETING_ACTIVITIES_READ))
                .thenReturn(TENANT);
        when(guard.requireActiveMembership(CALLER, AuthorityCatalog.MARKETING_ACTIVITIES_WRITE))
                .thenReturn(TENANT);
        when(activities.findById(eq(TENANT), any(MarketingActivityId.class)))
                .thenReturn(Optional.of(activity));
        when(tenantRepository.lockForTenantChanges(TENANT)).thenReturn(true);
        when(activities.findByIdForUpdate(eq(TENANT), any(MarketingActivityId.class)))
                .thenReturn(Optional.of(activity));
        when(activity.isActive()).thenReturn(true);

        MarketingActivityAttachmentTargetAccess access = new MarketingActivityAttachmentTargetAccess(
                guard, tenantRepository, activities);

        assertSame(TENANT, access.authorizeRead(CALLER, TARGET));
        assertTrue(access.lockForAttachmentMutation(CALLER, TARGET,
                AttachmentTargetOperation.CREATE_INTENT).attachmentsPermitted());
        verify(guard).requireActiveMembership(CALLER, AuthorityCatalog.MARKETING_ACTIVITIES_READ);
        verify(guard).requireActiveMembership(CALLER, AuthorityCatalog.MARKETING_ACTIVITIES_WRITE);
        verify(activities).findByIdForUpdate(TENANT, new MarketingActivityId(9L));
    }
}
