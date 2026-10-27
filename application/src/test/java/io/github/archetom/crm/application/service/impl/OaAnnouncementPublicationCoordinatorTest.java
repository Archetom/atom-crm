package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStore;
import io.github.archetom.crm.application.port.out.notification.NotificationPreferenceStore;
import io.github.archetom.crm.application.properties.NotificationDeliveryProperties;
import io.github.archetom.crm.application.properties.NotificationReminderProperties;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.domain.entity.OaAnnouncement;
import io.github.archetom.crm.domain.entity.OaAnnouncementAudienceSelector;
import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.model.OaAnnouncementPublicationSafeReason;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.OaAnnouncementRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.AuthorityCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OaAnnouncementPublicationCoordinatorTest {
    private static final TenantId TENANT = new TenantId(1L);
    private static final MemberId CREATOR = new MemberId(2L);
    private static final MemberId RECIPIENT = new MemberId(3L);
    private static final MemberId OPTED_OUT_RECIPIENT = new MemberId(4L);
    private static final MemberId REVOKED_RECIPIENT = new MemberId(5L);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 9, 0);

    @Test
    void atomicallyDerivesOnlyOptedInAndCurrentlyAuthorizedOaEmailDeliveries() {
        OaAnnouncementRepository announcements = mock(OaAnnouncementRepository.class);
        NotificationDeliveryStore deliveries = mock(NotificationDeliveryStore.class);
        NotificationPreferenceStore preferences = mock(NotificationPreferenceStore.class);
        TenantAuthorizationEvaluator authorizations = mock(TenantAuthorizationEvaluator.class);
        OaAnnouncement root = OaAnnouncement.create(TENANT, CREATOR, "Policy update", "Body",
                List.of(OaAnnouncementAudienceSelector.tenant()), NOW.minusMinutes(1));
        root.onPersisted(new OaAnnouncementId(8L), 0L, NOW.minusMinutes(1), NOW.minusMinutes(1));
        when(announcements.resolveActiveRecipientIds(TENANT, root.getId(), OaAnnouncement.MAX_RECIPIENTS + 1))
                .thenReturn(List.of(RECIPIENT, OPTED_OUT_RECIPIENT, REVOKED_RECIPIENT));
        when(preferences.oaAnnouncementEmailEnabled(TENANT,
                List.of(RECIPIENT, OPTED_OUT_RECIPIENT, REVOKED_RECIPIENT)))
                .thenReturn(Set.of(RECIPIENT, REVOKED_RECIPIENT));
        TenantAuthorizationEvaluator.Evaluation recipientAuthorization = authorized();
        when(authorizations.evaluateAll(TENANT, List.of(RECIPIENT, OPTED_OUT_RECIPIENT, REVOKED_RECIPIENT)))
                .thenReturn(Map.of(RECIPIENT, recipientAuthorization,
                        REVOKED_RECIPIENT, TenantAuthorizationEvaluator.Evaluation.empty()));

        OaAnnouncementPublicationCoordinator coordinator = new OaAnnouncementPublicationCoordinator(announcements,
                mock(DepartmentRepository.class), mock(OrganizationMembershipRepository.class), deliveries, preferences,
                authorizations, enabledDeliveryProperties(), new NotificationReminderProperties());

        assertTrue(coordinator.publish(root, NOW).published());

        verify(announcements).saveRecipientSnapshot(TENANT, root.getId(),
                List.of(RECIPIENT, OPTED_OUT_RECIPIENT, REVOKED_RECIPIENT), NOW);
        verify(deliveries).createEmailForOaAnnouncementRecipients(eq(TENANT), eq(root), eq(List.of(RECIPIENT)),
                eq(NOW), eq(Duration.ofDays(1)));
    }

    @Test
    void publishesTheInAppSnapshotWhenEmailDeliveryIsDisabled() {
        OaAnnouncementRepository announcements = mock(OaAnnouncementRepository.class);
        NotificationDeliveryStore deliveries = mock(NotificationDeliveryStore.class);
        NotificationPreferenceStore preferences = mock(NotificationPreferenceStore.class);
        TenantAuthorizationEvaluator authorizations = mock(TenantAuthorizationEvaluator.class);
        OaAnnouncement root = OaAnnouncement.create(TENANT, CREATOR, "Policy update", "Body",
                List.of(OaAnnouncementAudienceSelector.tenant()), NOW.minusMinutes(1));
        root.onPersisted(new OaAnnouncementId(8L), 0L, NOW.minusMinutes(1), NOW.minusMinutes(1));
        when(announcements.resolveActiveRecipientIds(TENANT, root.getId(), OaAnnouncement.MAX_RECIPIENTS + 1))
                .thenReturn(List.of(RECIPIENT));

        OaAnnouncementPublicationCoordinator coordinator = new OaAnnouncementPublicationCoordinator(announcements,
                mock(DepartmentRepository.class), mock(OrganizationMembershipRepository.class), deliveries, preferences,
                authorizations, new NotificationDeliveryProperties(), new NotificationReminderProperties());

        assertTrue(coordinator.publish(root, NOW).published());

        verify(announcements).saveRecipientSnapshot(TENANT, root.getId(), List.of(RECIPIENT), NOW);
        verifyNoInteractions(deliveries, preferences, authorizations);
    }

    @Test
    void rejectsUnavailableAttachmentsBeforeResolvingRecipientsOrCreatingAnyPublicationSideEffect() {
        OaAnnouncementRepository announcements = mock(OaAnnouncementRepository.class);
        AttachmentFileRepository files = mock(AttachmentFileRepository.class);
        AttachmentFile unavailable = mock(AttachmentFile.class);
        OaAnnouncement root = OaAnnouncement.create(TENANT, CREATOR, "Policy update", "Body",
                List.of(OaAnnouncementAudienceSelector.tenant()), NOW.minusMinutes(1));
        root.onPersisted(new OaAnnouncementId(8L), 0L, NOW.minusMinutes(1), NOW.minusMinutes(1));
        when(files.findActiveByTargetForUpdate(eq(TENANT), org.mockito.ArgumentMatchers.any(), eq(101)))
                .thenReturn(List.of(unavailable));

        OaAnnouncementPublicationCoordinator coordinator = new OaAnnouncementPublicationCoordinator(announcements,
                mock(DepartmentRepository.class), mock(OrganizationMembershipRepository.class), files);

        OaAnnouncementPublicationCoordinator.PublicationResult result = coordinator.publish(root, NOW);

        assertFalse(result.published());
        assertEquals(OaAnnouncementPublicationSafeReason.ATTACHMENT_UNAVAILABLE, result.rejectionReason());
        verify(announcements, org.mockito.Mockito.never()).resolveActiveRecipientIds(
                eq(TENANT), eq(root.getId()), org.mockito.ArgumentMatchers.anyInt());
        verify(announcements, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(announcements, org.mockito.Mockito.never()).saveRecipientSnapshot(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static TenantAuthorizationEvaluator.Evaluation authorized() {
        TenantAuthorizationEvaluator.Evaluation value = mock(TenantAuthorizationEvaluator.Evaluation.class);
        RoleGrant notifications = mock(RoleGrant.class);
        RoleGrant announcements = mock(RoleGrant.class);
        when(notifications.authorityCode()).thenReturn(new AuthorityCode(AuthorityCatalog.NOTIFICATIONS_READ));
        when(announcements.authorityCode()).thenReturn(new AuthorityCode(AuthorityCatalog.OA_ANNOUNCEMENTS_READ));
        when(value.activeMember()).thenReturn(true);
        when(value.effectiveGrants()).thenReturn(List.of(notifications, announcements));
        return value;
    }

    private static NotificationDeliveryProperties enabledDeliveryProperties() {
        NotificationDeliveryProperties value = new NotificationDeliveryProperties();
        value.setEnabled(true);
        value.setAdapterContract("TEST");
        value.setAdapterBuild("BUILD");
        value.setBindingRevision("BINDING");
        value.setActiveEndpointBindingKeyVersion("KEY");
        value.setEndpointBindingKeys(Map.of("KEY", "test-key"));
        return value;
    }
}
