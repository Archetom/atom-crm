package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OaAnnouncementQueryRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementScheduleRequest;
import io.github.archetom.crm.api.dto.request.OaAnnouncementVersionRequest;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.automation.BackgroundWorkObservability;
import io.github.archetom.crm.application.automation.OaAnnouncementPublicationMetric;
import io.github.archetom.crm.application.port.out.oa.OaAnnouncementPublicationStore;
import io.github.archetom.crm.application.properties.OaAnnouncementPublicationProperties;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.domain.entity.OaAnnouncement;
import io.github.archetom.crm.domain.entity.OaAnnouncementAudienceSelector;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.OaAnnouncementRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OaAnnouncementServiceImplTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId OWNER = new MemberId(7L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(
            OWNER.getValue(), TENANT.getValue(),
            Set.of(AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 19, 12, 0);

    private final OaAnnouncementRepository repository =
            mock(OaAnnouncementRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final MemberProfileRepository profiles = mock(MemberProfileRepository.class);
    private final OrganizationMembershipRepository memberships =
            mock(OrganizationMembershipRepository.class);
    private final OrganizationTenantRepository tenants =
            mock(OrganizationTenantRepository.class);
    private final OrganizationAccessGuard access = mock(OrganizationAccessGuard.class);
    private final BackgroundWorkObservability observability = mock(BackgroundWorkObservability.class);
    private final AttachmentParentLifecycle attachmentParentLifecycle = mock(AttachmentParentLifecycle.class);
    private OaAnnouncementServiceImpl service;

    @BeforeEach
    void setUp() {
        TestTransactionManager transactions = new TestTransactionManager();
        service = new OaAnnouncementServiceImpl(
                repository, departments, profiles, memberships, tenants,
                access, new CommandServiceTemplate("test-app", transactions),
                new QueryServiceTemplate("test-app", transactions),
                Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC),
                new OaAnnouncementPublicationCoordinator(repository, departments, memberships),
                mock(OaAnnouncementPublicationStore.class),
                new OaAnnouncementPublicationProperties(), new SchedulingProperties(),
                new AfterCommitExecutor(), observability, attachmentParentLifecycle);
        when(access.requireActiveMembership(CALLER,
                AuthorityCatalog.OA_ANNOUNCEMENTS_WRITE)).thenReturn(TENANT);
        when(tenants.lockForTenantChanges(TENANT)).thenReturn(true);
        when(memberships.findByMember(TENANT, OWNER)).thenReturn(Optional.of(
                OrganizationMembership.activate(TENANT, OWNER, null)));
    }

    @Test
    void recipientOverflowFailsWithoutPublishingOrSavingSnapshot() {
        OaAnnouncement announcement = OaAnnouncement.create(
                TENANT, OWNER, "Title", "Body",
                List.of(OaAnnouncementAudienceSelector.tenant()), NOW);
        announcement.onPersisted(new OaAnnouncementId(31L), 0L, NOW, NOW);
        when(repository.findByIdForUpdate(TENANT, announcement.getId()))
                .thenReturn(Optional.of(announcement));
        when(repository.resolveActiveRecipientIds(
                TENANT, announcement.getId(), OaAnnouncement.MAX_RECIPIENTS + 1))
                .thenReturn(LongStream.rangeClosed(
                                1, OaAnnouncement.MAX_RECIPIENTS + 1L)
                        .mapToObj(MemberId::new).toList());

        var result = service.publish(CALLER, announcement.getId().getValue(),
                new OaAnnouncementVersionRequest().setVersion(0L));

        assertFalse(result.isSuccess());
        assertEquals("002", result.getErrorContext().fetchRootError()
                .getErrorCode().getErrorSpecific());
        verify(repository, never()).save(eq(TENANT), any());
        verify(repository, never()).saveRecipientSnapshot(
                any(), any(), any(), any());
    }

    @Test
    void managementReadsRecheckActiveMembershipInsideQueryTransaction() {
        when(memberships.findByMember(TENANT, OWNER)).thenReturn(Optional.empty());

        var page = service.query(CALLER, new OaAnnouncementQueryRequest());
        var detail = service.get(CALLER, 31L);

        assertFalse(page.isSuccess());
        assertFalse(detail.isSuccess());
        verify(repository, never()).findPage(any(), any());
        verify(repository, never()).findById(any(), any());
    }

    @Test
    void scheduleUsesDatabaseUtcAndCreatesOneExactGeneration() {
        OaAnnouncement announcement = OaAnnouncement.create(
                TENANT, OWNER, "Title", "Body",
                List.of(OaAnnouncementAudienceSelector.tenant()), NOW);
        announcement.onPersisted(new OaAnnouncementId(32L), 0L, NOW, NOW);
        when(repository.findByIdForUpdate(TENANT, announcement.getId()))
                .thenReturn(Optional.of(announcement));
        when(repository.countByStatus(TENANT, io.github.archetom.crm.domain.model.OaAnnouncementStatus.SCHEDULED))
                .thenReturn(0L);
        when(repository.findStatistics(eq(TENANT), any())).thenReturn(java.util.Map.of());
        OaAnnouncementPublicationStore publications = mock(OaAnnouncementPublicationStore.class);
        when(publications.currentUtc()).thenReturn(NOW);
        OaAnnouncementPublicationProperties publicationProperties = new OaAnnouncementPublicationProperties();
        publicationProperties.setEnabled(true);
        TestTransactionManager transactions = new TestTransactionManager();
        service = new OaAnnouncementServiceImpl(repository, departments, profiles, memberships, tenants, access,
                new CommandServiceTemplate("test-app", transactions),
                new QueryServiceTemplate("test-app", transactions), Clock.fixed(
                        Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC),
                new OaAnnouncementPublicationCoordinator(repository, departments, memberships), publications,
                publicationProperties, new SchedulingProperties(), new AfterCommitExecutor(), observability,
                attachmentParentLifecycle);

        var result = service.schedule(CALLER, announcement.getId().getValue(),
                new OaAnnouncementScheduleRequest().setVersion(0L)
                        .setScheduledPublishTime(OffsetDateTime.of(
                                NOW.plusHours(10), ZoneOffset.ofHours(8))));

        assertEquals(true, result.isSuccess());
        assertEquals(io.github.archetom.crm.domain.model.OaAnnouncementStatus.SCHEDULED,
                announcement.getStatus());
        assertEquals(1L, announcement.getPublicationGeneration());
        verify(publications).create(eq(TENANT), eq(announcement.getId()), eq(1L),
                eq(NOW.plusHours(2)), eq(NOW.plusHours(2).plusDays(1)), eq(NOW));
        verify(observability).recordOaAnnouncementPublicationOutcome(
                OaAnnouncementPublicationMetric.SCHEDULED, 1);
    }

    @Test
    void scheduleRejectsUnavailableAttachmentsBeforeCreatingAnOccurrence() {
        OaAnnouncement announcement = OaAnnouncement.create(
                TENANT, OWNER, "Title", "Body",
                List.of(OaAnnouncementAudienceSelector.tenant()), NOW);
        announcement.onPersisted(new OaAnnouncementId(33L), 0L, NOW, NOW);
        AttachmentFileRepository files = mock(AttachmentFileRepository.class);
        AttachmentFile unavailable = mock(AttachmentFile.class);
        OaAnnouncementPublicationStore publications = mock(OaAnnouncementPublicationStore.class);
        OaAnnouncementPublicationProperties publicationProperties = new OaAnnouncementPublicationProperties();
        publicationProperties.setEnabled(true);
        TestTransactionManager transactions = new TestTransactionManager();
        service = new OaAnnouncementServiceImpl(repository, departments, profiles, memberships, tenants, access,
                new CommandServiceTemplate("test-app", transactions),
                new QueryServiceTemplate("test-app", transactions), Clock.fixed(
                        Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC),
                new OaAnnouncementPublicationCoordinator(repository, departments, memberships, files), publications,
                publicationProperties, new SchedulingProperties(), new AfterCommitExecutor(), observability,
                attachmentParentLifecycle);
        when(repository.findByIdForUpdate(TENANT, announcement.getId()))
                .thenReturn(Optional.of(announcement));
        when(publications.currentUtc()).thenReturn(NOW);
        when(files.findActiveByTargetForUpdate(eq(TENANT), eq(
                new AttachmentTarget(AttachmentTargetType.OA_ANNOUNCEMENT, announcement.getId().getValue())), eq(101)))
                .thenReturn(List.of(unavailable));

        var result = service.schedule(CALLER, announcement.getId().getValue(),
                new OaAnnouncementScheduleRequest().setVersion(0L)
                        .setScheduledPublishTime(OffsetDateTime.of(
                                NOW.plusHours(10), ZoneOffset.UTC)));

        assertFalse(result.isSuccess());
        verify(repository, never()).save(eq(TENANT), eq(announcement));
        verify(publications, never()).create(any(), any(), any(Long.class), any(), any(), any());
    }

    @Test
    void rescheduleCancelsTheOldOccurrenceBeforeLockingAttachments() {
        OaAnnouncement announcement = OaAnnouncement.create(
                TENANT, OWNER, "Title", "Body",
                List.of(OaAnnouncementAudienceSelector.tenant()), NOW);
        announcement.onPersisted(new OaAnnouncementId(34L), 0L, NOW, NOW);
        announcement.schedule(OWNER, NOW.plusHours(2), NOW);
        AttachmentFileRepository files = mock(AttachmentFileRepository.class);
        OaAnnouncementPublicationStore publications = mock(OaAnnouncementPublicationStore.class);
        OaAnnouncementPublicationProperties publicationProperties = new OaAnnouncementPublicationProperties();
        publicationProperties.setEnabled(true);
        TestTransactionManager transactions = new TestTransactionManager();
        service = new OaAnnouncementServiceImpl(repository, departments, profiles, memberships, tenants, access,
                new CommandServiceTemplate("test-app", transactions),
                new QueryServiceTemplate("test-app", transactions), Clock.fixed(
                        Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC),
                new OaAnnouncementPublicationCoordinator(repository, departments, memberships, files), publications,
                publicationProperties, new SchedulingProperties(), new AfterCommitExecutor(), observability,
                attachmentParentLifecycle);
        when(repository.findByIdForUpdate(TENANT, announcement.getId()))
                .thenReturn(Optional.of(announcement));
        when(repository.findStatistics(eq(TENANT), any())).thenReturn(java.util.Map.of());
        when(publications.currentUtc()).thenReturn(NOW);
        when(publications.cancelGeneration(TENANT, announcement.getId(), 1L, NOW,
                io.github.archetom.crm.domain.model.OaAnnouncementPublicationSafeReason.SUPERSEDED))
                .thenReturn(true);
        when(files.findActiveByTargetForUpdate(eq(TENANT), eq(
                new AttachmentTarget(AttachmentTargetType.OA_ANNOUNCEMENT, announcement.getId().getValue())), eq(101)))
                .thenReturn(List.of());

        var result = service.schedule(CALLER, announcement.getId().getValue(),
                new OaAnnouncementScheduleRequest().setVersion(0L)
                        .setScheduledPublishTime(OffsetDateTime.of(NOW.plusHours(10), ZoneOffset.UTC)));

        assertEquals(true, result.isSuccess());
        var order = inOrder(publications, files);
        order.verify(publications).cancelGeneration(TENANT, announcement.getId(), 1L, NOW,
                io.github.archetom.crm.domain.model.OaAnnouncementPublicationSafeReason.SUPERSEDED);
        order.verify(files).findActiveByTargetForUpdate(eq(TENANT), eq(
                new AttachmentTarget(AttachmentTargetType.OA_ANNOUNCEMENT, announcement.getId().getValue())), eq(101));
    }
}
