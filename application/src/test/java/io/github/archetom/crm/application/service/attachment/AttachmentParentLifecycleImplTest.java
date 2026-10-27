package io.github.archetom.crm.application.service.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.model.AttachmentByteState;
import io.github.archetom.crm.domain.model.AttachmentContentEvidence;
import io.github.archetom.crm.domain.model.AttachmentPublicationState;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AttachmentParentLifecycleImplTest {
    private static final Instant NOW = Instant.parse("2026-07-21T00:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Test
    void withdrawsEveryActiveRelationAndSchedulesTheMatchingDurableCleanup() {
        TenantId tenantId = new TenantId(1L);
        AttachmentTarget target = new AttachmentTarget(AttachmentTargetType.CUSTOMER, 7L);
        AttachmentFile stored = storedFile(11L);
        AttachmentFile uploading = uploadingFile(12L);
        AttachmentFileRepository fileRepository = mock(AttachmentFileRepository.class);
        AttachmentWorkStore workStore = mock(AttachmentWorkStore.class);
        when(fileRepository.findActiveByTargetForUpdate(tenantId, target)).thenReturn(List.of(stored, uploading));
        AttachmentParentLifecycle lifecycle = new AttachmentParentLifecycleImpl(fileRepository, workStore, policy(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        lifecycle.withdrawAll(tenantId, target);

        assertEquals(AttachmentPublicationState.WITHDRAWN, stored.getPublicationState());
        assertEquals(AttachmentByteState.REMOVING, stored.getByteState());
        assertEquals(AttachmentPublicationState.WITHDRAWN, uploading.getPublicationState());
        assertEquals(AttachmentByteState.UPLOADING, uploading.getByteState());
        verify(fileRepository).withdrawRelation(tenantId, target, stored.getId());
        verify(fileRepository).withdrawRelation(tenantId, target, uploading.getId());
        verify(fileRepository).save(tenantId, stored);
        verify(fileRepository).save(tenantId, uploading);

        ArgumentCaptor<AttachmentWorkStore.JobRequest> jobs = ArgumentCaptor.forClass(AttachmentWorkStore.JobRequest.class);
        verify(workStore, times(4)).createJobIfAbsent(jobs.capture());
        assertEquals(1, jobs.getAllValues().stream().filter(job -> job.fileId().sameValueAs(stored.getId())
                && job.kind() == AttachmentWorkStore.JobKind.DELETION).count());
        assertEquals(1, jobs.getAllValues().stream().filter(job -> job.fileId().sameValueAs(uploading.getId())
                && job.kind() == AttachmentWorkStore.JobKind.INCOMPLETE_ABORT).count());
        assertEquals(2, jobs.getAllValues().stream().filter(job -> job.kind()
                == AttachmentWorkStore.JobKind.RETENTION_SWEEP).count());
    }

    @Test
    void withdrawalStillClosesRelationsWhenTheAttachmentRuntimeIsDisabled() {
        TenantId tenantId = new TenantId(1L);
        AttachmentTarget target = new AttachmentTarget(AttachmentTargetType.MARKETING_ACTIVITY, 7L);
        AttachmentFile uploading = uploadingFile(12L);
        AttachmentFileRepository fileRepository = mock(AttachmentFileRepository.class);
        AttachmentWorkStore workStore = mock(AttachmentWorkStore.class);
        when(fileRepository.findActiveByTargetForUpdate(tenantId, target)).thenReturn(List.of(uploading));
        AttachmentParentLifecycle lifecycle = new AttachmentParentLifecycleImpl(fileRepository, workStore,
                AttachmentRuntimePolicy.disabled(), Clock.fixed(NOW, ZoneOffset.UTC));

        lifecycle.withdrawAll(tenantId, target);

        assertEquals(AttachmentPublicationState.WITHDRAWN, uploading.getPublicationState());
        verify(fileRepository).withdrawRelation(tenantId, target, uploading.getId());
        verify(fileRepository).save(tenantId, uploading);
        verify(workStore, never()).createJobIfAbsent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rearmsOnlyPausedInspectionAndPublicationWorkWhenOwnershipIsRestored() {
        TenantId tenantId = new TenantId(1L);
        AttachmentTarget target = new AttachmentTarget(AttachmentTargetType.CONTACT, 7L);
        AttachmentFile stored = storedFile(11L);
        AttachmentFileRepository fileRepository = mock(AttachmentFileRepository.class);
        AttachmentWorkStore workStore = mock(AttachmentWorkStore.class);
        when(fileRepository.findActiveByTargetForUpdate(tenantId, target)).thenReturn(List.of(stored));
        AttachmentParentLifecycle lifecycle = new AttachmentParentLifecycleImpl(fileRepository, workStore, policy(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        lifecycle.resumeAll(tenantId, target);

        verify(workStore).resumePausedJobs(tenantId, stored.getId(), NOW, NOW.plusSeconds(1), NOW);
    }

    @Test
    void terminalCutoffKeepsAvailableRelationsAndWithdrawsOnlyIncompleteCandidates() {
        TenantId tenantId = new TenantId(1L);
        AttachmentTarget target = new AttachmentTarget(AttachmentTargetType.OA_REQUEST, 7L);
        AttachmentFile available = availableFile(11L);
        AttachmentFile uploading = uploadingFile(12L);
        AttachmentFileRepository fileRepository = mock(AttachmentFileRepository.class);
        AttachmentWorkStore workStore = mock(AttachmentWorkStore.class);
        when(fileRepository.findActiveByTargetForUpdate(tenantId, target, 101))
                .thenReturn(List.of(available, uploading));
        AttachmentParentLifecycle lifecycle = new AttachmentParentLifecycleImpl(fileRepository, workStore, policy(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        lifecycle.cutoffNonAvailable(tenantId, target);

        assertEquals(AttachmentPublicationState.AVAILABLE, available.getPublicationState());
        assertEquals(AttachmentPublicationState.WITHDRAWN, uploading.getPublicationState());
        verify(fileRepository, never()).withdrawRelation(tenantId, target, available.getId());
        verify(fileRepository).withdrawRelation(tenantId, target, uploading.getId());
        verify(workStore, times(2)).createJobIfAbsent(org.mockito.ArgumentMatchers.any());
    }

    private static AttachmentFile storedFile(long id) {
        AttachmentFile file = uploadingFile(id);
        file.acceptStored(new AttachmentContentEvidence(4, new byte[32], 1), "application/pdf", LOCAL_NOW);
        return file;
    }

    private static AttachmentFile availableFile(long id) {
        AttachmentFile file = storedFile(id);
        file.markInspectionClean(LOCAL_NOW);
        file.publish(LOCAL_NOW);
        return file;
    }

    private static AttachmentFile uploadingFile(long id) {
        AttachmentFile file = AttachmentFile.createIntent(new TenantId(1L), new MemberId(2L),
                "attachment.pdf", "application/pdf", 4, null, LOCAL_NOW);
        file.onPersisted(new AttachmentFileId(id), 0L, LOCAL_NOW, LOCAL_NOW);
        return file;
    }

    private static AttachmentRuntimePolicy policy() {
        Duration second = Duration.ofSeconds(1);
        return new AttachmentRuntimePolicy(true, 16, 64, 64, 2, 2, 2, 2, 128, 1,
                second, second, second, second, second, second, second, second,
                Duration.ofSeconds(5), Duration.ofMillis(20), 2, second, second, 1);
    }
}
