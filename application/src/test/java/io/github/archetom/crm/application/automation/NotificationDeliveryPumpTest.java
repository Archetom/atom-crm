package io.github.archetom.crm.application.automation;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.application.port.out.notification.NotificationChannel;
import io.github.archetom.crm.application.port.out.notification.NotificationChannelAdapter;
import io.github.archetom.crm.application.port.out.notification.NotificationChannelAdapterRegistry;
import io.github.archetom.crm.application.port.out.notification.NotificationDelivery;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryClaim;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryEffectCertainty;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryOutcomeCounts;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStatus;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStore;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliverySourceAdmissionRegistry;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliverySourceKind;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchCommand;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchGuarantee;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchResult;
import io.github.archetom.crm.application.port.out.notification.NotificationPreferenceStore;
import io.github.archetom.crm.application.port.out.notification.NotificationReadinessSnapshot;
import io.github.archetom.crm.application.properties.NotificationDeliveryProperties;
import io.github.archetom.crm.application.properties.NotificationReminderProperties;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.entity.Notification;
import io.github.archetom.crm.domain.entity.OaAnnouncement;
import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.model.WorkTaskPriority;
import io.github.archetom.crm.domain.model.WorkTaskStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.NotificationRepository;
import io.github.archetom.crm.domain.repository.OaAnnouncementRepository;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;
import io.github.archetom.crm.domain.valueobject.Email;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.NotificationId;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeliveryPumpTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 22, 12, 0);
    private static final TenantId TENANT = new TenantId(1L);
    private static final MemberId MEMBER = new MemberId(2L);
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void dispatchesOnlyAfterAdmissionAndSettlesProviderAcceptance() {
        NotificationDeliveryStore store = mock(NotificationDeliveryStore.class);
        stubMaintenance(store);
        NotificationPreferenceStore preferences = mock(NotificationPreferenceStore.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        WorkTaskRepository tasks = mock(WorkTaskRepository.class);
        MemberProfileRepository profiles = mock(MemberProfileRepository.class);
        TenantAuthorizationEvaluator authorizations = mock(TenantAuthorizationEvaluator.class);
        NotificationChannelAdapter adapter = adapter(NotificationDispatchResult.accepted());
        CommandServiceTemplate commands = commandTemplateThatExecutesOperations();
        NotificationDelivery delivery = leasedDelivery();
        Notification notification = notification();
        WorkTask task = task();
        MemberProfile profile = profile();
        TenantAuthorizationEvaluator.Evaluation authorization = authorized();

        when(store.currentUtc()).thenReturn(NOW);
        when(store.claimDue(any(), any(), eq(NOW), any(Duration.class), eq(5)))
                .thenReturn(new NotificationDeliveryClaim(Optional.of(delivery), 0, 0));
        when(store.findClaimedForUpdate(eq(TENANT), eq(9L), any(), eq(3L), eq(NOW)))
                .thenReturn(Optional.of(delivery));
        when(store.beginDispatch(eq(TENANT), eq(9L), any(), eq(3L), any(), any(), anyString(), anyString(), eq(NOW)))
                .thenReturn(true);
        when(store.settleDispatch(eq(TENANT), eq(9L), any(), eq(3L), any(), eq(NOW), eq(5), any()))
                .thenReturn(Optional.of(NotificationDeliveryStatus.ACCEPTED));
        when(preferences.emailEnabled(TENANT, MEMBER)).thenReturn(true);
        when(notifications.findByIdForDeliveryAdmission(TENANT, new NotificationId(4L))).thenReturn(Optional.of(notification));
        when(tasks.findByIdForReminderEffect(TENANT, new WorkTaskId(5L))).thenReturn(Optional.of(task));
        when(profiles.findById(MEMBER)).thenReturn(Optional.of(profile));
        when(authorizations.evaluate(TENANT, MEMBER)).thenReturn(authorization);

        new NotificationDeliveryPump(store, preferences, notifications, tasks, profiles, authorizations,
                new NotificationChannelAdapterRegistry(List.of(adapter)), enabledProperties(), reminderProperties(), commands,
                mock(BackgroundWorkObservability.class)).runOnce();

        ArgumentCaptor<NotificationDispatchCommand> command = ArgumentCaptor.forClass(NotificationDispatchCommand.class);
        verify(adapter).dispatch(command.capture(), eq(Duration.ofSeconds(30)));
        assertEquals(9L, command.getValue().deliveryId());
        assertEquals("b27c:9:" + delivery.payloadDigest(), command.getValue().idempotencyKey());
        verify(store).settleDispatch(eq(TENANT), eq(9L), any(), eq(3L),
                eq(io.github.archetom.crm.application.port.out.notification.NotificationDispatchOutcome.ACCEPTED),
                eq(NOW), eq(5), any());
        ArgumentCaptor<String> binding = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bindingVersion = ArgumentCaptor.forClass(String.class);
        verify(store).beginDispatch(eq(TENANT), eq(9L), any(), eq(3L), any(), any(), binding.capture(),
                bindingVersion.capture(), eq(NOW));
        assertEquals("TEST_KEY_V1", bindingVersion.getValue());
        assertNotEquals(sha256("member@example.test"), binding.getValue());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void dispatchesTypedOaRecipientWithoutReadingTheAnnouncementBody() {
        NotificationDeliveryStore store = mock(NotificationDeliveryStore.class);
        stubMaintenance(store);
        NotificationPreferenceStore preferences = mock(NotificationPreferenceStore.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        WorkTaskRepository tasks = mock(WorkTaskRepository.class);
        MemberProfileRepository profiles = mock(MemberProfileRepository.class);
        TenantAuthorizationEvaluator authorizations = mock(TenantAuthorizationEvaluator.class);
        OaAnnouncementRepository announcements = mock(OaAnnouncementRepository.class);
        OaAnnouncement announcement = mock(OaAnnouncement.class);
        LocalDateTime published = NOW.minusMinutes(2);
        when(announcement.getId()).thenReturn(new OaAnnouncementId(12L));
        when(announcement.getStatus()).thenReturn(io.github.archetom.crm.domain.model.OaAnnouncementStatus.PUBLISHED);
        when(announcement.getTitle()).thenReturn("Policy update");
        when(announcement.getPublishedTime()).thenReturn(published);
        NotificationDelivery delivery = new NotificationDelivery(9L, TENANT,
                NotificationDeliverySourceKind.OA_ANNOUNCEMENT_RECIPIENT, null, MEMBER, null,
                new OaAnnouncementId(12L), NotificationChannel.EMAIL,
                "B28B_OA_ANNOUNCEMENT_EMAIL_V1",
                io.github.archetom.crm.application.port.out.notification.NotificationDeliveryTemplate
                        .oaAnnouncementPayloadDigest(announcement), null, null, NotificationDeliveryStatus.LEASED,
                1, NOW.minusMinutes(1), NOW.plusDays(1), OWNER, 3L, NOW.plusMinutes(1), null,
                NotificationDeliveryEffectCertainty.NO_EFFECT, null, null, null, null);
        NotificationChannelAdapter adapter = mock(NotificationChannelAdapter.class);
        when(adapter.channel()).thenReturn(NotificationChannel.EMAIL);
        when(adapter.readiness()).thenReturn(new NotificationReadinessSnapshot(NotificationChannel.EMAIL,
                "TEST", "BUILD", "BINDING", NotificationDispatchGuarantee.idempotent(Duration.ofMinutes(5)),
                "B28B_OA_ANNOUNCEMENT_EMAIL_V1", NOW.plusMinutes(1)));
        when(adapter.dispatch(any(), any())).thenReturn(NotificationDispatchResult.accepted());
        when(store.currentUtc()).thenReturn(NOW);
        when(store.claimDue(any(), any(), eq(NOW), any(Duration.class), eq(5)))
                .thenReturn(new NotificationDeliveryClaim(Optional.of(delivery), 0, 0));
        when(store.findClaimedForUpdate(eq(TENANT), eq(9L), any(), eq(3L), eq(NOW)))
                .thenReturn(Optional.of(delivery));
        when(store.beginDispatch(eq(TENANT), eq(9L), any(), eq(3L), any(), any(), anyString(), anyString(), eq(NOW)))
                .thenReturn(true);
        when(store.settleDispatch(eq(TENANT), eq(9L), any(), eq(3L), any(), eq(NOW), eq(5), any()))
                .thenReturn(Optional.of(NotificationDeliveryStatus.ACCEPTED));
        when(announcements.findByIdForUpdate(TENANT, new OaAnnouncementId(12L))).thenReturn(Optional.of(announcement));
        when(announcements.hasRecipientForDeliveryAdmission(TENANT, new OaAnnouncementId(12L), MEMBER))
                .thenReturn(true);
        when(preferences.oaAnnouncementEmailEnabled(TENANT, MEMBER)).thenReturn(true);
        MemberProfile recipientProfile = profile();
        when(profiles.findById(MEMBER)).thenReturn(Optional.of(recipientProfile));
        TenantAuthorizationEvaluator.Evaluation oaAuthorization = authorizedOa();
        when(authorizations.evaluate(TENANT, MEMBER)).thenReturn(oaAuthorization);

        NotificationDeliveryAdmissionSupport support = new NotificationDeliveryAdmissionSupport(store, preferences,
                profiles, enabledProperties());
        NotificationDeliverySourceAdmissionRegistry registry = new NotificationDeliverySourceAdmissionRegistry(List.of(
                new WorkTaskNotificationDeliverySourceAdmission(support, notifications, tasks, authorizations),
                new OaAnnouncementRecipientDeliverySourceAdmission(support, announcements, authorizations)));
        new NotificationDeliveryPump(store, preferences, notifications, tasks, profiles, authorizations,
                new NotificationChannelAdapterRegistry(List.of(adapter)), enabledProperties(), reminderProperties(),
                commandTemplateThatExecutesOperations(), mock(BackgroundWorkObservability.class), registry).runOnce();

        ArgumentCaptor<NotificationDispatchCommand> command = ArgumentCaptor.forClass(NotificationDispatchCommand.class);
        verify(adapter).dispatch(command.capture(), any());
        assertEquals(12L, command.getValue().notificationId());
        assertEquals("OA_ANNOUNCEMENT", command.getValue().kind());
        assertEquals("Policy update", command.getValue().title());
        assertEquals(published, command.getValue().dueTime());
        assertTrue(command.getValue().idempotencyKey().startsWith("b28b:oa:9:"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void preferenceOffSuppressesBeforeAdapterInvocation() {
        NotificationDeliveryStore store = mock(NotificationDeliveryStore.class);
        stubMaintenance(store);
        NotificationPreferenceStore preferences = mock(NotificationPreferenceStore.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        WorkTaskRepository tasks = mock(WorkTaskRepository.class);
        NotificationChannelAdapter adapter = adapter(NotificationDispatchResult.accepted());
        NotificationDelivery delivery = leasedDelivery();
        CommandServiceTemplate commands = commandTemplateThatExecutesOperations();
        Notification notification = notification();
        WorkTask task = task();

        when(store.currentUtc()).thenReturn(NOW);
        when(store.claimDue(any(), any(), eq(NOW), any(Duration.class), eq(5)))
                .thenReturn(new NotificationDeliveryClaim(Optional.of(delivery), 0, 0));
        when(store.findClaimedForUpdate(eq(TENANT), eq(9L), any(), eq(3L), eq(NOW)))
                .thenReturn(Optional.of(delivery));
        when(notifications.findByIdForDeliveryAdmission(TENANT, new NotificationId(4L))).thenReturn(Optional.of(notification));
        when(tasks.findByIdForReminderEffect(TENANT, new WorkTaskId(5L))).thenReturn(Optional.of(task));
        when(preferences.emailEnabled(TENANT, MEMBER)).thenReturn(false);
        when(store.suppressClaim(eq(TENANT), eq(9L), any(), eq(3L), eq("PREFERENCE_OFF"), eq(NOW)))
                .thenReturn(NotificationDeliveryStatus.SUPPRESSED);

        new NotificationDeliveryPump(store, preferences, notifications, tasks, mock(MemberProfileRepository.class),
                mock(TenantAuthorizationEvaluator.class), new NotificationChannelAdapterRegistry(List.of(adapter)),
                enabledProperties(), reminderProperties(), commands, mock(BackgroundWorkObservability.class)).runOnce();

        verify(store).suppressClaim(eq(TENANT), eq(9L), any(), eq(3L), eq("PREFERENCE_OFF"), eq(NOW));
        verify(adapter, never()).dispatch(any(), any());
    }

    @Test
    void readinessMustMatchTheDeploymentIdentityAndRemainShortLived() {
        NotificationReadinessSnapshot snapshot = new NotificationReadinessSnapshot(NotificationChannel.EMAIL,
                "TEST", "BUILD", "BINDING", NotificationDispatchGuarantee.idempotent(Duration.ofMinutes(5)),
                "B27C_EMAIL_V1", NOW.plusMinutes(5));

        assertTrue(snapshot.readyAt(NotificationChannel.EMAIL, "B27C_EMAIL_V1", "TEST", "BUILD", "BINDING", NOW));
        assertFalse(snapshot.readyAt(NotificationChannel.EMAIL, "B27C_EMAIL_V1", "OLD", "BUILD", "BINDING", NOW));
        NotificationReadinessSnapshot overlong = new NotificationReadinessSnapshot(NotificationChannel.EMAIL,
                "TEST", "BUILD", "BINDING", NotificationDispatchGuarantee.idempotent(Duration.ofMinutes(5)),
                "B27C_EMAIL_V1", NOW.plusMinutes(6));
        assertFalse(overlong.readyAt(NotificationChannel.EMAIL, "B27C_EMAIL_V1", "TEST", "BUILD", "BINDING", NOW));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void unsupportedReadinessLeavesDueDeliveryUnclaimed() {
        NotificationDeliveryStore store = mock(NotificationDeliveryStore.class);
        stubMaintenance(store);
        when(store.currentUtc()).thenReturn(NOW);
        NotificationChannelAdapter adapter = mock(NotificationChannelAdapter.class);
        when(adapter.channel()).thenReturn(NotificationChannel.EMAIL);
        when(adapter.readiness()).thenReturn(new NotificationReadinessSnapshot(NotificationChannel.EMAIL,
                "TEST", "BUILD", "BINDING", NotificationDispatchGuarantee.unsupported(),
                "B27C_EMAIL_V1", NOW.plusMinutes(1)));

        new NotificationDeliveryPump(store, mock(NotificationPreferenceStore.class), mock(NotificationRepository.class),
                mock(WorkTaskRepository.class), mock(MemberProfileRepository.class),
                mock(TenantAuthorizationEvaluator.class), new NotificationChannelAdapterRegistry(List.of(adapter)),
                enabledProperties(), reminderProperties(), commandTemplateThatExecutesOperations(),
                mock(BackgroundWorkObservability.class)).runOnce();

        verify(store, never()).claimDue(any(), any(), any(), any(), anyInt());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void claimsOnlyTemplatesWithCurrentAdapterReadiness() {
        NotificationDeliveryStore store = mock(NotificationDeliveryStore.class);
        stubMaintenance(store);
        when(store.currentUtc()).thenReturn(NOW);
        when(store.claimDue(eq(List.of("B27C_EMAIL_V1")), any(), eq(NOW), any(Duration.class), eq(5)))
                .thenReturn(new NotificationDeliveryClaim(Optional.empty(), 0, 0));

        new NotificationDeliveryPump(store, mock(NotificationPreferenceStore.class), mock(NotificationRepository.class),
                mock(WorkTaskRepository.class), mock(MemberProfileRepository.class),
                mock(TenantAuthorizationEvaluator.class),
                new NotificationChannelAdapterRegistry(List.of(adapter(NotificationDispatchResult.accepted()))),
                enabledProperties(), reminderProperties(), commandTemplateThatExecutesOperations(),
                mock(BackgroundWorkObservability.class)).runOnce();

        verify(store).claimDue(eq(List.of("B27C_EMAIL_V1")), any(), eq(NOW), any(Duration.class), eq(5));
    }

    @Test
    void acceptsUnicodeCodePointBoundedTitlesAtTheDispatchBoundary() {
        String maximumTitle = "🚀".repeat(200);
        String overlongTitle = "🚀".repeat(201);

        assertDoesNotThrow(() -> new NotificationDeliveryDispatchContent(
                NotificationDeliverySourceKind.OA_ANNOUNCEMENT_RECIPIENT, 4L,
                "B28B_OA_ANNOUNCEMENT_EMAIL_V1", "a".repeat(64), "b28b:oa:9:key", "PURPOSE_V1",
                "OA_ANNOUNCEMENT", maximumTitle, "NORMAL", NOW));
        assertDoesNotThrow(() -> new NotificationDispatchCommand(9L, 4L, NotificationChannel.EMAIL,
                "B28B_OA_ANNOUNCEMENT_EMAIL_V1", "a".repeat(64), "b28b:oa:9:key",
                new io.github.archetom.crm.application.port.out.notification.NotificationEndpoint(
                        NotificationChannel.EMAIL, "member@example.test"),
                "OA_ANNOUNCEMENT", maximumTitle, "NORMAL", NOW));

        assertThrows(IllegalArgumentException.class, () -> new NotificationDeliveryDispatchContent(
                NotificationDeliverySourceKind.OA_ANNOUNCEMENT_RECIPIENT, 4L,
                "B28B_OA_ANNOUNCEMENT_EMAIL_V1", "a".repeat(64), "b28b:oa:9:key", "PURPOSE_V1",
                "OA_ANNOUNCEMENT", overlongTitle, "NORMAL", NOW));
        assertThrows(IllegalArgumentException.class, () -> new NotificationDispatchCommand(9L, 4L,
                NotificationChannel.EMAIL, "B28B_OA_ANNOUNCEMENT_EMAIL_V1", "a".repeat(64), "b28b:oa:9:key",
                new io.github.archetom.crm.application.port.out.notification.NotificationEndpoint(
                        NotificationChannel.EMAIL, "member@example.test"),
                "OA_ANNOUNCEMENT", overlongTitle, "NORMAL", NOW));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void reservesSettlementTimeFromTheDispatchBudget() {
        NotificationDeliveryStore store = mock(NotificationDeliveryStore.class);
        stubMaintenance(store);
        NotificationPreferenceStore preferences = mock(NotificationPreferenceStore.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        WorkTaskRepository tasks = mock(WorkTaskRepository.class);
        MemberProfileRepository profiles = mock(MemberProfileRepository.class);
        TenantAuthorizationEvaluator authorizations = mock(TenantAuthorizationEvaluator.class);
        NotificationChannelAdapter adapter = adapter(NotificationDispatchResult.accepted());
        NotificationDelivery delivery = leasedDelivery(NOW.plusSeconds(2));
        Notification notification = notification();
        WorkTask currentTask = task();
        MemberProfile currentProfile = profile();
        TenantAuthorizationEvaluator.Evaluation authorization = authorized();
        when(store.currentUtc()).thenReturn(NOW);
        when(store.claimDue(any(), any(), eq(NOW), any(Duration.class), eq(5)))
                .thenReturn(new NotificationDeliveryClaim(Optional.of(delivery), 0, 0));
        when(store.findClaimedForUpdate(eq(TENANT), eq(9L), any(), eq(3L), eq(NOW))).thenReturn(Optional.of(delivery));
        when(store.beginDispatch(eq(TENANT), eq(9L), any(), eq(3L), any(), any(), anyString(), anyString(), eq(NOW)))
                .thenReturn(true);
        when(store.settleDispatch(eq(TENANT), eq(9L), any(), eq(3L), any(), eq(NOW), eq(5), any()))
                .thenReturn(Optional.of(NotificationDeliveryStatus.ACCEPTED));
        when(preferences.emailEnabled(TENANT, MEMBER)).thenReturn(true);
        when(notifications.findByIdForDeliveryAdmission(TENANT, new NotificationId(4L))).thenReturn(Optional.of(notification));
        when(tasks.findByIdForReminderEffect(TENANT, new WorkTaskId(5L))).thenReturn(Optional.of(currentTask));
        when(profiles.findById(MEMBER)).thenReturn(Optional.of(currentProfile));
        when(authorizations.evaluate(TENANT, MEMBER)).thenReturn(authorization);

        new NotificationDeliveryPump(store, preferences, notifications, tasks, profiles, authorizations,
                new NotificationChannelAdapterRegistry(List.of(adapter)), enabledProperties(), reminderProperties(),
                commandTemplateThatExecutesOperations(), mock(BackgroundWorkObservability.class)).runOnce();

        verify(adapter).dispatch(any(), eq(Duration.ofSeconds(1)));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void lostSettlementAfterProviderInvocationFailsTheLane() {
        NotificationDeliveryStore store = mock(NotificationDeliveryStore.class);
        stubMaintenance(store);
        NotificationPreferenceStore preferences = mock(NotificationPreferenceStore.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        WorkTaskRepository tasks = mock(WorkTaskRepository.class);
        MemberProfileRepository profiles = mock(MemberProfileRepository.class);
        TenantAuthorizationEvaluator authorizations = mock(TenantAuthorizationEvaluator.class);
        NotificationChannelAdapter adapter = adapter(NotificationDispatchResult.accepted());
        NotificationDelivery delivery = leasedDelivery();
        Notification notification = notification();
        WorkTask currentTask = task();
        MemberProfile currentProfile = profile();
        TenantAuthorizationEvaluator.Evaluation authorization = authorized();
        when(store.currentUtc()).thenReturn(NOW);
        when(store.claimDue(any(), any(), eq(NOW), any(Duration.class), eq(5)))
                .thenReturn(new NotificationDeliveryClaim(Optional.of(delivery), 0, 0));
        when(store.findClaimedForUpdate(eq(TENANT), eq(9L), any(), eq(3L), eq(NOW))).thenReturn(Optional.of(delivery));
        when(store.beginDispatch(eq(TENANT), eq(9L), any(), eq(3L), any(), any(), anyString(), anyString(), eq(NOW)))
                .thenReturn(true);
        when(store.settleDispatch(eq(TENANT), eq(9L), any(), eq(3L), any(), eq(NOW), eq(5), any()))
                .thenReturn(Optional.empty());
        when(preferences.emailEnabled(TENANT, MEMBER)).thenReturn(true);
        when(notifications.findByIdForDeliveryAdmission(TENANT, new NotificationId(4L))).thenReturn(Optional.of(notification));
        when(tasks.findByIdForReminderEffect(TENANT, new WorkTaskId(5L))).thenReturn(Optional.of(currentTask));
        when(profiles.findById(MEMBER)).thenReturn(Optional.of(currentProfile));
        when(authorizations.evaluate(TENANT, MEMBER)).thenReturn(authorization);

        assertThrows(IllegalStateException.class, () -> new NotificationDeliveryPump(store, preferences, notifications,
                tasks, profiles, authorizations, new NotificationChannelAdapterRegistry(List.of(adapter)),
                enabledProperties(), reminderProperties(), commandTemplateThatExecutesOperations(),
                mock(BackgroundWorkObservability.class)).runOnce());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void recordsCommittedDeadlineMaintenanceOutcomes() {
        NotificationDeliveryStore store = mock(NotificationDeliveryStore.class);
        when(store.currentUtc()).thenReturn(NOW);
        when(store.expireOverdue(eq(NOW), eq(100))).thenReturn(new NotificationDeliveryOutcomeCounts(1, 2, 3));
        BackgroundWorkObservability observability = mock(BackgroundWorkObservability.class);

        new NotificationDeliveryPump(store, mock(NotificationPreferenceStore.class), mock(NotificationRepository.class),
                mock(WorkTaskRepository.class), mock(MemberProfileRepository.class),
                mock(TenantAuthorizationEvaluator.class), new NotificationChannelAdapterRegistry(List.of(adapter(
                NotificationDispatchResult.accepted()))), new NotificationDeliveryProperties(), reminderProperties(),
                commandTemplateThatExecutesOperations(), observability).runOnce();

        verify(observability).recordNotificationDeliveryOutcome(NotificationDeliveryMetric.SUPPRESSED, 1);
        verify(observability).recordNotificationDeliveryOutcome(NotificationDeliveryMetric.AMBIGUOUS, 2);
        verify(observability).recordNotificationDeliveryOutcome(NotificationDeliveryMetric.DEAD, 3);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void recordsSuppressionOnlyAfterTheAdmissionTransactionCommits() {
        NotificationDeliveryStore store = mock(NotificationDeliveryStore.class);
        stubMaintenance(store);
        NotificationPreferenceStore preferences = mock(NotificationPreferenceStore.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        WorkTaskRepository tasks = mock(WorkTaskRepository.class);
        BackgroundWorkObservability observability = mock(BackgroundWorkObservability.class);
        NotificationDelivery delivery = leasedDelivery();
        Notification notification = notification();
        WorkTask task = task();
        when(store.currentUtc()).thenReturn(NOW);
        when(store.claimDue(any(), any(), eq(NOW), any(Duration.class), eq(5)))
                .thenReturn(new NotificationDeliveryClaim(Optional.of(delivery), 0, 0));
        when(store.findClaimedForUpdate(eq(TENANT), eq(9L), any(), eq(3L), eq(NOW)))
                .thenReturn(Optional.of(delivery));
        when(notifications.findByIdForDeliveryAdmission(TENANT, new NotificationId(4L))).thenReturn(Optional.of(notification));
        when(tasks.findByIdForReminderEffect(TENANT, new WorkTaskId(5L))).thenReturn(Optional.of(task));
        when(preferences.emailEnabled(TENANT, MEMBER)).thenReturn(false);
        when(store.suppressClaim(eq(TENANT), eq(9L), any(), eq(3L), eq("PREFERENCE_OFF"), eq(NOW)))
                .thenReturn(NotificationDeliveryStatus.SUPPRESSED);
        when(store.recoverNoDispatchFailure(eq(TENANT), eq(9L), any(), eq(3L), eq(NOW), eq(5), any()))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> new NotificationDeliveryPump(store, preferences, notifications,
                tasks, mock(MemberProfileRepository.class), mock(TenantAuthorizationEvaluator.class),
                new NotificationChannelAdapterRegistry(List.of(adapter(NotificationDispatchResult.accepted()))),
                enabledProperties(), reminderProperties(), commandTemplateThatFailsAdmissionAfterExecute(), observability).runOnce());

        verify(observability, never()).recordNotificationDeliveryOutcome(
                eq(NotificationDeliveryMetric.SUPPRESSED), anyLong());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void preservesAmbiguityWhenAnIdempotentRetryIsNoLongerAdmissible() {
        NotificationDeliveryStore store = mock(NotificationDeliveryStore.class);
        stubMaintenance(store);
        NotificationPreferenceStore preferences = mock(NotificationPreferenceStore.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        WorkTaskRepository tasks = mock(WorkTaskRepository.class);
        BackgroundWorkObservability observability = mock(BackgroundWorkObservability.class);
        NotificationDelivery delivery = previouslyDispatchedDelivery("a".repeat(64));
        Notification notification = notification();
        WorkTask currentTask = task();
        when(store.currentUtc()).thenReturn(NOW);
        when(store.claimDue(any(), any(), eq(NOW), any(Duration.class), eq(5)))
                .thenReturn(new NotificationDeliveryClaim(Optional.of(delivery), 0, 0));
        when(store.findClaimedForUpdate(eq(TENANT), eq(9L), any(), eq(3L), eq(NOW))).thenReturn(Optional.of(delivery));
        when(notifications.findByIdForDeliveryAdmission(TENANT, new NotificationId(4L))).thenReturn(Optional.of(notification));
        when(tasks.findByIdForReminderEffect(TENANT, new WorkTaskId(5L))).thenReturn(Optional.of(currentTask));
        when(preferences.emailEnabled(TENANT, MEMBER)).thenReturn(false);
        when(store.suppressClaim(eq(TENANT), eq(9L), any(), eq(3L), eq("PREFERENCE_OFF"), eq(NOW)))
                .thenReturn(NotificationDeliveryStatus.AMBIGUOUS);

        new NotificationDeliveryPump(store, preferences, notifications, tasks, mock(MemberProfileRepository.class),
                mock(TenantAuthorizationEvaluator.class), new NotificationChannelAdapterRegistry(List.of(adapter(
                NotificationDispatchResult.accepted()))), enabledProperties(), reminderProperties(), commandTemplateThatExecutesOperations(),
                observability).runOnce();

        verify(observability).recordNotificationDeliveryOutcome(NotificationDeliveryMetric.AMBIGUOUS, 1);
        verify(observability, never()).recordNotificationDeliveryOutcome(NotificationDeliveryMetric.SUPPRESSED, 1);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void endpointDriftTurnsAnIdempotentRetryAmbiguousBeforeProviderInvocation() {
        NotificationDeliveryStore store = mock(NotificationDeliveryStore.class);
        stubMaintenance(store);
        NotificationPreferenceStore preferences = mock(NotificationPreferenceStore.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        WorkTaskRepository tasks = mock(WorkTaskRepository.class);
        MemberProfileRepository profiles = mock(MemberProfileRepository.class);
        TenantAuthorizationEvaluator authorizations = mock(TenantAuthorizationEvaluator.class);
        NotificationChannelAdapter adapter = adapter(NotificationDispatchResult.accepted());
        NotificationDelivery delivery = previouslyDispatchedDelivery("a".repeat(64));
        Notification notification = notification();
        WorkTask currentTask = task();
        MemberProfile currentProfile = profile();
        TenantAuthorizationEvaluator.Evaluation authorization = authorized();
        when(store.currentUtc()).thenReturn(NOW);
        when(store.claimDue(any(), any(), eq(NOW), any(Duration.class), eq(5)))
                .thenReturn(new NotificationDeliveryClaim(Optional.of(delivery), 0, 0));
        when(store.findClaimedForUpdate(eq(TENANT), eq(9L), any(), eq(3L), eq(NOW))).thenReturn(Optional.of(delivery));
        when(notifications.findByIdForDeliveryAdmission(TENANT, new NotificationId(4L))).thenReturn(Optional.of(notification));
        when(tasks.findByIdForReminderEffect(TENANT, new WorkTaskId(5L))).thenReturn(Optional.of(currentTask));
        when(preferences.emailEnabled(TENANT, MEMBER)).thenReturn(true);
        when(profiles.findById(MEMBER)).thenReturn(Optional.of(currentProfile));
        when(authorizations.evaluate(TENANT, MEMBER)).thenReturn(authorization);
        when(store.suppressClaim(eq(TENANT), eq(9L), any(), eq(3L), eq("ENDPOINT_DRIFT"), eq(NOW)))
                .thenReturn(NotificationDeliveryStatus.AMBIGUOUS);

        new NotificationDeliveryPump(store, preferences, notifications, tasks, profiles, authorizations,
                new NotificationChannelAdapterRegistry(List.of(adapter)), enabledProperties(), reminderProperties(),
                commandTemplateThatExecutesOperations(), mock(BackgroundWorkObservability.class)).runOnce();

        verify(store, never()).beginDispatch(any(), anyLong(), any(), anyLong(), any(), any(), anyString(), anyString(), any());
        verify(adapter, never()).dispatch(any(), any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void dispatchTimeoutSettlesAmbiguouslyAndFailsTheLaneInvocation() throws Exception {
        NotificationDeliveryStore store = mock(NotificationDeliveryStore.class);
        stubMaintenance(store);
        NotificationPreferenceStore preferences = mock(NotificationPreferenceStore.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        WorkTaskRepository tasks = mock(WorkTaskRepository.class);
        MemberProfileRepository profiles = mock(MemberProfileRepository.class);
        TenantAuthorizationEvaluator authorizations = mock(TenantAuthorizationEvaluator.class);
        NotificationChannelAdapter adapter = adapter(NotificationDispatchResult.accepted());
        when(adapter.dispatch(any(), any())).thenAnswer(invocation -> {
            Thread.sleep(Duration.ofSeconds(10));
            return NotificationDispatchResult.accepted();
        });
        NotificationDelivery delivery = leasedDelivery();
        Notification notification = notification();
        WorkTask currentTask = task();
        MemberProfile currentProfile = profile();
        TenantAuthorizationEvaluator.Evaluation authorization = authorized();
        when(store.currentUtc()).thenReturn(NOW);
        when(store.claimDue(any(), any(), eq(NOW), any(Duration.class), eq(5)))
                .thenReturn(new NotificationDeliveryClaim(Optional.of(delivery), 0, 0));
        when(store.findClaimedForUpdate(eq(TENANT), eq(9L), any(), eq(3L), eq(NOW))).thenReturn(Optional.of(delivery));
        when(store.beginDispatch(eq(TENANT), eq(9L), any(), eq(3L), any(), any(), anyString(), anyString(), eq(NOW)))
                .thenReturn(true);
        when(store.settleDispatch(eq(TENANT), eq(9L), any(), eq(3L),
                eq(io.github.archetom.crm.application.port.out.notification.NotificationDispatchOutcome.AMBIGUOUS),
                eq(NOW), eq(5), any())).thenReturn(Optional.of(NotificationDeliveryStatus.RETRY_WAIT));
        when(preferences.emailEnabled(TENANT, MEMBER)).thenReturn(true);
        when(notifications.findByIdForDeliveryAdmission(TENANT, new NotificationId(4L))).thenReturn(Optional.of(notification));
        when(tasks.findByIdForReminderEffect(TENANT, new WorkTaskId(5L))).thenReturn(Optional.of(currentTask));
        when(profiles.findById(MEMBER)).thenReturn(Optional.of(currentProfile));
        when(authorizations.evaluate(TENANT, MEMBER)).thenReturn(authorization);
        NotificationDeliveryProperties properties = enabledProperties();
        properties.setDispatchDeadline(Duration.ofSeconds(1));

        assertThrows(IllegalStateException.class, () -> new NotificationDeliveryPump(store, preferences, notifications,
                tasks, profiles, authorizations, new NotificationChannelAdapterRegistry(List.of(adapter)), properties,
                reminderProperties(), commandTemplateThatExecutesOperations(), mock(BackgroundWorkObservability.class)).runOnce());

        verify(store).settleDispatch(eq(TENANT), eq(9L), any(), eq(3L),
                eq(io.github.archetom.crm.application.port.out.notification.NotificationDispatchOutcome.AMBIGUOUS),
                eq(NOW), eq(5), any());
    }

    private static NotificationChannelAdapter adapter(NotificationDispatchResult result) {
        NotificationChannelAdapter adapter = mock(NotificationChannelAdapter.class);
        when(adapter.channel()).thenReturn(NotificationChannel.EMAIL);
        when(adapter.readiness()).thenReturn(new NotificationReadinessSnapshot(NotificationChannel.EMAIL,
                "TEST", "BUILD", "BINDING", NotificationDispatchGuarantee.idempotent(Duration.ofMinutes(5)),
                "B27C_EMAIL_V1", NOW.plusMinutes(1)));
        when(adapter.dispatch(any(), any())).thenReturn(result);
        return adapter;
    }

    private static NotificationDelivery leasedDelivery() {
        return leasedDelivery(NOW.plusMinutes(1));
    }

    private static NotificationDelivery leasedDelivery(LocalDateTime leaseUntil) {
        return new NotificationDelivery(9L, TENANT, new NotificationId(4L), MEMBER, new WorkTaskId(5L),
                NotificationChannel.EMAIL, "B27C_EMAIL_V1",
                io.github.archetom.crm.application.port.out.notification.NotificationDeliveryTemplate.payloadDigest(notification()),
                null, null,
                NotificationDeliveryStatus.LEASED,
                1, NOW.minusMinutes(1), NOW.plusDays(1), OWNER, 3L, leaseUntil, null,
                NotificationDeliveryEffectCertainty.NO_EFFECT, null, null, null, null);
    }

    private static NotificationDelivery previouslyDispatchedDelivery(String endpointDigest) {
        return new NotificationDelivery(9L, TENANT, new NotificationId(4L), MEMBER, new WorkTaskId(5L),
                NotificationChannel.EMAIL, "B27C_EMAIL_V1",
                io.github.archetom.crm.application.port.out.notification.NotificationDeliveryTemplate.payloadDigest(notification()),
                endpointDigest, "TEST_KEY_V1", NotificationDeliveryStatus.LEASED,
                2, NOW.minusMinutes(1), NOW.plusDays(1), OWNER, 3L, NOW.plusMinutes(1),
                io.github.archetom.crm.application.port.out.notification.NotificationDispatchGuaranteeKind.IDEMPOTENT_SEND,
                NotificationDeliveryEffectCertainty.AMBIGUOUS, NOW.plusMinutes(5), NOW.minusMinutes(1), null, null);
    }

    private static Notification notification() {
        Notification value = Notification.create(TENANT, MEMBER,
                io.github.archetom.crm.domain.model.NotificationKind.WORK_TASK_REMINDER,
                new io.github.archetom.crm.domain.valueobject.WorkTaskReminderOccurrenceId(3L), new WorkTaskId(5L),
                7L, "Task", WorkTaskPriority.NORMAL, NOW, NOW);
        value.onPersisted(new NotificationId(4L), NOW, NOW);
        return value;
    }

    private static WorkTask task() {
        WorkTask task = mock(WorkTask.class);
        when(task.getStatus()).thenReturn(WorkTaskStatus.TODO);
        when(task.getNotificationProjectionVersion()).thenReturn(7L);
        when(task.isAssignedTo(MEMBER)).thenReturn(true);
        when(task.isCreatedBy(MEMBER)).thenReturn(false);
        when(task.getFollowerMemberIds()).thenReturn(java.util.Set.of());
        return task;
    }

    private static MemberProfile profile() {
        MemberProfile profile = mock(MemberProfile.class);
        when(profile.isActive()).thenReturn(true);
        when(profile.getEmail()).thenReturn(new Email("member@example.test"));
        return profile;
    }

    private static TenantAuthorizationEvaluator.Evaluation authorized() {
        TenantAuthorizationEvaluator.Evaluation value = mock(TenantAuthorizationEvaluator.Evaluation.class);
        when(value.activeMember()).thenReturn(true);
        RoleGrant notifications = mock(RoleGrant.class);
        RoleGrant workTasks = mock(RoleGrant.class);
        var notificationsCode = new io.github.archetom.crm.domain.valueobject.AuthorityCode(AuthorityCatalog.NOTIFICATIONS_READ);
        var workTasksCode = new io.github.archetom.crm.domain.valueobject.AuthorityCode(AuthorityCatalog.WORK_TASKS_READ);
        when(notifications.authorityCode()).thenReturn(notificationsCode);
        when(workTasks.authorityCode()).thenReturn(workTasksCode);
        when(value.effectiveGrants()).thenReturn(List.of(notifications, workTasks));
        return value;
    }

    private static TenantAuthorizationEvaluator.Evaluation authorizedOa() {
        TenantAuthorizationEvaluator.Evaluation value = mock(TenantAuthorizationEvaluator.Evaluation.class);
        when(value.activeMember()).thenReturn(true);
        RoleGrant notifications = mock(RoleGrant.class);
        RoleGrant announcements = mock(RoleGrant.class);
        when(notifications.authorityCode()).thenReturn(
                new io.github.archetom.crm.domain.valueobject.AuthorityCode(AuthorityCatalog.NOTIFICATIONS_READ));
        when(announcements.authorityCode()).thenReturn(
                new io.github.archetom.crm.domain.valueobject.AuthorityCode(AuthorityCatalog.OA_ANNOUNCEMENTS_READ));
        when(value.effectiveGrants()).thenReturn(List.of(notifications, announcements));
        return value;
    }

    private static NotificationDeliveryProperties enabledProperties() {
        NotificationDeliveryProperties value = new NotificationDeliveryProperties();
        value.setEnabled(true);
        value.setAdapterContract("TEST");
        value.setAdapterBuild("BUILD");
        value.setBindingRevision("BINDING");
        value.setActiveEndpointBindingKeyVersion("TEST_KEY_V1");
        value.setEndpointBindingKeys(java.util.Map.of("TEST_KEY_V1", "test-only-endpoint-binding-key"));
        return value;
    }

    private static NotificationReminderProperties reminderProperties() {
        return new NotificationReminderProperties();
    }

    private static void stubMaintenance(NotificationDeliveryStore store) {
        when(store.expireOverdue(eq(NOW), eq(100))).thenReturn(NotificationDeliveryOutcomeCounts.none());
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CommandServiceTemplate commandTemplateThatExecutesOperations() {
        CommandServiceTemplate template = mock(CommandServiceTemplate.class);
        when(template.execute(any(), any(ServiceOperation.class), any())).thenAnswer(invocation -> {
            Result result = new Result<>();
            result.setData(((ServiceOperation) invocation.getArgument(1)).execute());
            result.setSuccess(true);
            return result;
        });
        return template;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CommandServiceTemplate commandTemplateThatFailsAdmissionAfterExecute() {
        CommandServiceTemplate template = mock(CommandServiceTemplate.class);
        AtomicInteger invocation = new AtomicInteger();
        when(template.execute(any(), any(ServiceOperation.class), any())).thenAnswer(call -> {
            Result result = new Result<>();
            Object data = ((ServiceOperation) call.getArgument(1)).execute();
            if (invocation.incrementAndGet() != 3) {
                result.setSuccess(true);
                result.setData(data);
            } else {
                result.setSuccess(false);
            }
            return result;
        });
        return template;
    }
}
