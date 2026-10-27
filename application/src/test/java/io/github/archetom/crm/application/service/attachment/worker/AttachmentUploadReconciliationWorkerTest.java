package io.github.archetom.crm.application.service.attachment.worker;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.MarketingFileDraftId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachmentUploadReconciliationWorkerTest {

    @Test
    void invalidMarketingDraftIsSettledBeforeAnyExternalStorageProbe() throws Exception {
        TenantId tenant = new TenantId(1L);
        AttachmentFileId fileId = new AttachmentFileId(2L);
        Instant now = Instant.parse("2026-07-23T00:00:00Z");
        AttachmentWorkStore.JobFence fence = new AttachmentWorkStore.JobFence(
                tenant, fileId, 1, 3L, 1L, new byte[] {1}, 1L);
        AttachmentWorkStore.LeasedJob job = new AttachmentWorkStore.LeasedJob(
                fence, AttachmentWorkStore.JobKind.UPLOAD_RECONCILIATION,
                now.plusSeconds(30), false, false);
        AttachmentFile file = AttachmentFile.createPublicMarketingIntent(tenant,
                new MarketingFileDraftId(4L), "resume.pdf", "application/pdf", 4,
                null, LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        AttachmentWorkerSupport.Subject invalidSubject = new AttachmentWorkerSupport.Subject(
                tenant, null, fileId, file, false, false, true);
        AttachmentWorkerSupport support = mock(AttachmentWorkerSupport.class);
        AttachmentStorage storage = mock(AttachmentStorage.class);
        CommandServiceTemplate commandTemplate = mock(CommandServiceTemplate.class);
        when(support.claim(eq(AttachmentWorkStore.JobKind.UPLOAD_RECONCILIATION), any()))
                .thenReturn(List.of(job));
        when(support.leaseMayBeCurrent(fence)).thenReturn(true);
        when(support.heartbeat(job)).thenReturn(true);
        when(support.lockSubjectAndAssertLease(fence)).thenReturn(invalidSubject);
        when(commandTemplate.execute(any(), any())).thenAnswer(invocation -> {
            ServiceOperation<?> operation = invocation.getArgument(1);
            Object data = operation.execute();
            Result<Object> result = new Result<>();
            result.setData(data);
            result.setSuccess(true);
            return result;
        });

        new AttachmentUploadReconciliationWorker(support, storage, commandTemplate).runOnce();

        verify(support).settleRequired(eq(fence), isNull());
        verify(storage, never()).probe(any());
        verify(support, never()).planReady(any(), any());
    }
}
