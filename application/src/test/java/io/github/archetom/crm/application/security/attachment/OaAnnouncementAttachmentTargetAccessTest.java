package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.domain.entity.OaAnnouncement;
import io.github.archetom.crm.domain.entity.OaAnnouncementAudienceSelector;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OaAnnouncementRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OaAnnouncementAttachmentTargetAccessTest {
    private static final TenantId TENANT = new TenantId(1L);
    private static final MemberId MANAGER = new MemberId(2L);
    private static final MemberId RECIPIENT = new MemberId(3L);
    private static final AttachmentTarget TARGET =
            new AttachmentTarget(AttachmentTargetType.OA_ANNOUNCEMENT, 9L);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 9, 0);

    @Test
    void permitsOnlyOaManagersToMutateDraftAttachments() {
        OrganizationAccessGuard guard = mock(OrganizationAccessGuard.class);
        OrganizationTenantRepository tenants = mock(OrganizationTenantRepository.class);
        OaAnnouncementRepository announcements = mock(OaAnnouncementRepository.class);
        AuthenticatedCaller manager = new AuthenticatedCaller(MANAGER.getValue(), TENANT.getValue(),
                Set.of(AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE));
        OaAnnouncement draft = draft();
        when(guard.requireActiveMembership(manager, AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE)).thenReturn(TENANT);
        when(announcements.findById(TENANT, new OaAnnouncementId(9L))).thenReturn(Optional.of(draft));
        when(tenants.lockForTenantChanges(TENANT)).thenReturn(true);
        when(announcements.findByIdForUpdate(TENANT, new OaAnnouncementId(9L)))
                .thenReturn(Optional.of(draft));

        OaAnnouncementAttachmentTargetAccess access = new OaAnnouncementAttachmentTargetAccess(
                guard, tenants, announcements);

        assertSame(TENANT, access.authorizeRead(manager, TARGET));
        assertTrue(access.lockForAttachmentMutation(manager, TARGET,
                AttachmentTargetOperation.CREATE_INTENT).attachmentsPermitted());
        verify(announcements).findByIdForUpdate(TENANT, new OaAnnouncementId(9L));
    }

    @Test
    void permitsPublishedRecipientDownloadButNeverRecipientMutation() {
        OrganizationAccessGuard guard = mock(OrganizationAccessGuard.class);
        OrganizationTenantRepository tenants = mock(OrganizationTenantRepository.class);
        OaAnnouncementRepository announcements = mock(OaAnnouncementRepository.class);
        AuthenticatedCaller recipient = new AuthenticatedCaller(RECIPIENT.getValue(), TENANT.getValue(),
                Set.of(AuthorityCatalog.OA_ANNOUNCEMENTS_READ));
        OaAnnouncement published = published();
        when(guard.requireActiveMembership(recipient, AuthorityCatalog.OA_ANNOUNCEMENTS_READ)).thenReturn(TENANT);
        when(announcements.findById(TENANT, new OaAnnouncementId(9L))).thenReturn(Optional.of(published));
        when(tenants.lockForTenantChanges(TENANT)).thenReturn(true);
        when(announcements.findByIdForUpdate(TENANT, new OaAnnouncementId(9L)))
                .thenReturn(Optional.of(published));
        when(announcements.hasRecipientSnapshot(TENANT, new OaAnnouncementId(9L), RECIPIENT))
                .thenReturn(true);

        OaAnnouncementAttachmentTargetAccess access = new OaAnnouncementAttachmentTargetAccess(
                guard, tenants, announcements);

        assertSame(TENANT, access.authorizeRead(recipient, TARGET));
        assertTrue(access.lockForAttachmentMutation(recipient, TARGET,
                AttachmentTargetOperation.DOWNLOAD).attachmentsPermitted());
        assertThrows(RuntimeException.class, () -> access.lockForAttachmentMutation(recipient, TARGET,
                AttachmentTargetOperation.WITHDRAW));
        verify(announcements, org.mockito.Mockito.times(2)).hasRecipientSnapshot(TENANT,
                new OaAnnouncementId(9L), RECIPIENT);
        verify(announcements, never()).hasRecipientForDeliveryAdmission(TENANT,
                new OaAnnouncementId(9L), RECIPIENT);
    }

    @Test
    void doesNotLetManagersReadPublishedAttachmentsWithoutRecipientAdmission() {
        OrganizationAccessGuard guard = mock(OrganizationAccessGuard.class);
        OrganizationTenantRepository tenants = mock(OrganizationTenantRepository.class);
        OaAnnouncementRepository announcements = mock(OaAnnouncementRepository.class);
        AuthenticatedCaller manager = new AuthenticatedCaller(MANAGER.getValue(), TENANT.getValue(),
                Set.of(AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE));
        when(guard.requireActiveMembership(manager, AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE)).thenReturn(TENANT);
        when(announcements.findById(TENANT, new OaAnnouncementId(9L))).thenReturn(Optional.of(published()));

        OaAnnouncementAttachmentTargetAccess access = new OaAnnouncementAttachmentTargetAccess(
                guard, tenants, announcements);

        assertThrows(RuntimeException.class, () -> access.authorizeRead(manager, TARGET));
    }

    private static OaAnnouncement draft() {
        OaAnnouncement value = OaAnnouncement.create(TENANT, MANAGER, "Title", "Body",
                List.of(OaAnnouncementAudienceSelector.member(RECIPIENT)), NOW);
        value.onPersisted(new OaAnnouncementId(9L), 0L, NOW, NOW);
        return value;
    }

    private static OaAnnouncement published() {
        OaAnnouncement value = draft();
        value.publish(NOW.plusMinutes(1));
        return value;
    }
}
