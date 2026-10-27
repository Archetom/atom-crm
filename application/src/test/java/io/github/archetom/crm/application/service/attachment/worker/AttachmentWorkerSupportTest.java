package io.github.archetom.crm.application.service.attachment.worker;

import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorageReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccessRegistry;
import io.github.archetom.crm.application.service.impl.MarketingFormPolicy;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.MarketingFileDraft;
import io.github.archetom.crm.domain.entity.MarketingForm;
import io.github.archetom.crm.domain.entity.MarketingFormField;
import io.github.archetom.crm.domain.model.AttachmentPublicationState;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.MarketingFileDraftRepository;
import io.github.archetom.crm.domain.repository.MarketingFormRepository;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MarketingFileDraftId;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachmentWorkerSupportTest {
    private static final TenantId TENANT = new TenantId(1L);

    @Test
    void serialWorkerClaimsOnlyOneLeasePerInvocation() {
        AttachmentWorkStore workStore = mock(AttachmentWorkStore.class);
        when(workStore.claimDueJobs(any(), any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(java.util.List.of());
        AttachmentWorkerSupport support = new AttachmentWorkerSupport(workStore, mock(AttachmentFileRepository.class),
                mock(AttachmentTargetAccessRegistry.class), mock(AttachmentStorageReadinessResolver.class),
                policy(), Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC));

        support.claim(AttachmentWorkStore.JobKind.INSPECTION, new byte[] {1});

        verify(workStore).claimDueJobs(eq(AttachmentWorkStore.JobKind.INSPECTION), any(), any(), any(), eq(1),
                anyInt(), any());
    }

    @Test
    void heartbeatGuardRenewsLeaseDuringSlowExternalWork() throws Exception {
        AttachmentWorkStore workStore = mock(AttachmentWorkStore.class);
        when(workStore.heartbeat(any(), any(), any())).thenReturn(true);
        AttachmentWorkerSupport support = new AttachmentWorkerSupport(workStore, mock(AttachmentFileRepository.class),
                mock(AttachmentTargetAccessRegistry.class), mock(AttachmentStorageReadinessResolver.class),
                policy(), Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC));
        AttachmentWorkStore.LeasedJob job = new AttachmentWorkStore.LeasedJob(
                new AttachmentWorkStore.JobFence(new TenantId(1L), new AttachmentFileId(2L), 1, 3L, 1L,
                        new byte[] {1}, 1L), AttachmentWorkStore.JobKind.INSPECTION,
                Instant.parse("2026-07-20T00:01:00Z"), false, false);
        try (var lease = support.heartbeatGuard(job)) {
            Thread.sleep(90);
            assertTrue(lease.isCurrent());
        }
        verify(workStore, atLeast(3)).heartbeat(any(), any(), any());
    }

    @Test
    void policyInvalidMarketingCandidateUsesFormThenLeadGuardThenDraftAndSchedulesCleanup() {
        Instant now = Instant.parse("2026-07-20T00:00:00Z");
        AttachmentWorkStore workStore = mock(AttachmentWorkStore.class);
        AttachmentFileRepository files = mock(AttachmentFileRepository.class);
        MarketingFileDraftRepository drafts = mock(MarketingFileDraftRepository.class);
        MarketingFormRepository forms = mock(MarketingFormRepository.class);
        CustomizationObjectLock objectLock = mock(CustomizationObjectLock.class);
        MarketingFormPolicy formPolicy = mock(MarketingFormPolicy.class);
        AttachmentWorkerSupport support = new AttachmentWorkerSupport(workStore, files, null, drafts,
                forms, objectLock, formPolicy, mock(AttachmentTargetAccessRegistry.class),
                mock(AttachmentStorageReadinessResolver.class), policy(), Clock.fixed(now, ZoneOffset.UTC));

        MarketingForm form = MarketingForm.create(TENANT, new MemberId(11L), new MemberId(11L),
                "candidate-public-form", "Candidate form", "Candidate form", null, null, null,
                List.of(new MarketingFormField(new FieldCode("lead_name"), "Name", null, null, true, 1)));
        form.onPersisted(new MarketingFormId(3L), 7L, LocalDateTime.ofInstant(now, ZoneOffset.UTC),
                LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        // Form remains INACTIVE. A queued worker must therefore withdraw, not inspect/reconcile it.
        MarketingFileDraft draft = MarketingFileDraft.create(TENANT, form.getId(), 7L, new byte[32],
                LocalDateTime.ofInstant(now.plusSeconds(60), ZoneOffset.UTC),
                LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        draft.onPersisted(new MarketingFileDraftId(4L), 1L, LocalDateTime.ofInstant(now, ZoneOffset.UTC),
                LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        AttachmentFile file = AttachmentFile.createPublicMarketingIntent(TENANT, draft.getId(), "resume.pdf",
                "application/pdf", 4, null, LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        AttachmentFileId fileId = new AttachmentFileId(5L);
        file.onPersisted(fileId, 1L, LocalDateTime.ofInstant(now, ZoneOffset.UTC),
                LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        var staged = new MarketingFileDraftRepository.StagedFile(null, file, 0);

        when(drafts.findForStagedFile(TENANT, fileId)).thenReturn(Optional.of(draft));
        when(forms.findByIdForUpdate(TENANT, form.getId())).thenReturn(Optional.of(form));
        when(drafts.findForUpdate(TENANT, draft.getId())).thenReturn(Optional.of(draft));
        when(drafts.findStagedFilesForUpdate(TENANT, draft.getId())).thenReturn(List.of(staged));

        AttachmentWorkerSupport.Subject subject = support.lockSubject(new AttachmentWorkStore.JobFence(
                TENANT, fileId, 1, 9L, 1L, new byte[] {1}, 1L));

        assertFalse(subject.attachmentsPermitted());
        assertTrue(subject.stagedFileField());
        assertEquals(AttachmentPublicationState.WITHDRAWN, file.getPublicationState());
        var order = inOrder(drafts, forms, objectLock);
        order.verify(drafts).findForStagedFile(TENANT, fileId);
        order.verify(forms).findByIdForUpdate(TENANT, form.getId());
        order.verify(objectLock).acquire(TENANT, io.github.archetom.crm.domain.model.BusinessObjectType.LEAD);
        order.verify(drafts).findForUpdate(TENANT, draft.getId());
        verify(drafts).withdrawStagedFiles(TENANT, draft.getId());
        verify(drafts).save(TENANT, draft);
        verify(files).save(TENANT, file);
        verify(workStore, org.mockito.Mockito.times(2)).createJobIfAbsent(any());
        verify(formPolicy, never()).validate(any(), any(), any(), anyBoolean());
    }

    private static AttachmentRuntimePolicy policy() {
        Duration second = Duration.ofSeconds(1);
        return new AttachmentRuntimePolicy(true, 16, 64, 64, 2, 2, 2, 2, 128, 1,
                second, second, second, second, second, second, second, second,
                Duration.ofSeconds(5), Duration.ofMillis(20), 2, second, second, 1);
    }
}
