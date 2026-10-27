package io.github.archetom.crm.application.service.attachment.worker;

import io.github.archetom.crm.application.port.out.attachment.AttachmentContentDetector;
import io.github.archetom.crm.application.port.out.attachment.AttachmentContentScanner;
import io.github.archetom.crm.application.port.out.attachment.AttachmentInspectionReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.model.AttachmentContentEvidence;
import io.github.archetom.crm.domain.model.AttachmentInspectionState;
import io.github.archetom.crm.domain.model.AttachmentPublicationState;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AttachmentPausedTargetWorkerTest {
    private static final TenantId TENANT = new TenantId(1L);
    private static final AttachmentFileId FILE_ID = new AttachmentFileId(2L);
    private static final AttachmentTarget TARGET = new AttachmentTarget(AttachmentTargetType.CUSTOMER, 3L);
    private static final Instant NOW = Instant.parse("2026-07-21T00:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Test
    void pausedTargetDoesNotInspectOrMakeALateFileClean() {
        AttachmentWorkerSupport support = mock(AttachmentWorkerSupport.class);
        AttachmentStorage storage = mock(AttachmentStorage.class);
        AttachmentContentDetector detector = mock(AttachmentContentDetector.class);
        AttachmentContentScanner scanner = mock(AttachmentContentScanner.class);
        AttachmentInspectionReadinessResolver readiness = mock(AttachmentInspectionReadinessResolver.class);
        AttachmentWorkStore.LeasedJob job = job(AttachmentWorkStore.JobKind.INSPECTION);
        AttachmentFile file = storedFile(false);
        pausedJob(support, job, file);

        new AttachmentInspectionWorker(support, storage, detector, scanner, readiness, commandTemplate()).runOnce();

        assertEquals(AttachmentInspectionState.PENDING, file.getInspectionState());
        assertEquals(AttachmentPublicationState.PRIVATE, file.getPublicationState());
        verify(support).deferForPause(job.fence());
        verify(support, never()).retry(any(), any());
        verify(support, never()).fileRepository();
        verify(support, never()).settleRequired(any(), any());
        verifyNoInteractions(storage, detector, scanner, readiness);
    }

    @Test
    void pausedTargetDoesNotPublishALateCleanFile() {
        AttachmentWorkerSupport support = mock(AttachmentWorkerSupport.class);
        AttachmentWorkStore.LeasedJob job = job(AttachmentWorkStore.JobKind.PUBLICATION);
        AttachmentFile file = storedFile(true);
        pausedJob(support, job, file);

        new AttachmentPublicationWorker(support, commandTemplate()).runOnce();

        assertEquals(AttachmentInspectionState.CLEAN, file.getInspectionState());
        assertEquals(AttachmentPublicationState.PRIVATE, file.getPublicationState());
        verify(support).deferForPause(job.fence());
        verify(support, never()).retry(any(), any());
        verify(support, never()).fileRepository();
        verify(support, never()).settleRequired(any(), any());
    }

    private static void pausedJob(AttachmentWorkerSupport support, AttachmentWorkStore.LeasedJob job,
                                  AttachmentFile file) {
        when(support.claim(eq(job.kind()), any())).thenReturn(List.of(job));
        when(support.lockSubjectAndAssertLease(job.fence())).thenReturn(
                new AttachmentWorkerSupport.Subject(TENANT, TARGET, FILE_ID, file, false, true));
        when(support.now()).thenReturn(NOW);
    }

    private static AttachmentWorkStore.LeasedJob job(AttachmentWorkStore.JobKind kind) {
        AttachmentWorkStore.JobFence fence = new AttachmentWorkStore.JobFence(TENANT, FILE_ID,
                1L, 4L, 1L, new byte[] {1}, 1L);
        return new AttachmentWorkStore.LeasedJob(fence, kind, NOW.plusSeconds(60), false, false);
    }

    private static AttachmentFile storedFile(boolean clean) {
        AttachmentFile file = AttachmentFile.createIntent(TENANT, new MemberId(4L),
                "attachment.pdf", "application/pdf", 4L, null, LOCAL_NOW);
        file.onPersisted(FILE_ID, 0L, LOCAL_NOW, LOCAL_NOW);
        file.acceptStored(new AttachmentContentEvidence(4L, new byte[32], 1L), "application/pdf", LOCAL_NOW);
        if (clean) file.markInspectionClean(LOCAL_NOW);
        return file;
    }

    private static CommandServiceTemplate commandTemplate() {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new CommandServiceTemplate("attachment-worker-test", transactions);
    }
}
