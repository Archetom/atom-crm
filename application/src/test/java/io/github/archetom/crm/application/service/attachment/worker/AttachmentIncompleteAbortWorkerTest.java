package io.github.archetom.crm.application.service.attachment.worker;

import io.github.archetom.crm.application.port.out.attachment.AttachmentOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorageReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.port.out.attachment.PlannedObjectLocator;
import io.github.archetom.crm.application.port.out.attachment.StorageBindingRef;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccessRegistry;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachmentIncompleteAbortWorkerTest {
    @Test
    void foreignGenerationMismatchNeverSettlesTheAggregateAsRemoved() throws Exception {
        Instant now = Instant.parse("2026-07-20T00:00:00Z");
        AttachmentWorkStore workStore = mock(AttachmentWorkStore.class);
        AttachmentStorage storage = mock(AttachmentStorage.class);
        StorageBindingRef binding = new StorageBindingRef(new byte[] {1});
        byte[] generation = new byte[32]; generation[0] = 7;
        AttachmentWorkStore.JobFence fence = new AttachmentWorkStore.JobFence(new TenantId(1L), new AttachmentFileId(2L),
                1, 3, 1, new byte[] {4}, 1);
        AttachmentWorkStore.LeasedJob job = new AttachmentWorkStore.LeasedJob(fence,
                AttachmentWorkStore.JobKind.INCOMPLETE_ABORT, now.plusSeconds(60), false, false);
        AttachmentWorkStore.ContentPlan plan = new AttachmentWorkStore.ContentPlan(new TenantId(1L), new AttachmentFileId(2L),
                1, 0, binding, new PlannedObjectLocator(new byte[43]), generation, 1, null, null, now.plusSeconds(60));
        when(workStore.claimDueJobs(eq(AttachmentWorkStore.JobKind.INCOMPLETE_ABORT), any(), any(), any(),
                anyInt(), anyInt(), any())).thenReturn(List.of(job));
        when(workStore.isLeaseCurrent(eq(fence), any())).thenReturn(true);
        when(workStore.heartbeat(eq(fence), any(), any())).thenReturn(true);
        when(workStore.findContentPlan(new TenantId(1L), new AttachmentFileId(2L))).thenReturn(Optional.of(plan));
        when(storage.probe(any())).thenReturn(new AttachmentStorage.ProbeResult(AttachmentStorage.ProbeState.MISMATCH,
                null, null));
        AttachmentStorageReadinessResolver readiness = mock(AttachmentStorageReadinessResolver.class);
        when(readiness.resolve(any(), any())).thenReturn(new AttachmentStorageReadinessResolver.Snapshot(
                io.github.archetom.crm.application.port.out.attachment.AttachmentReadiness.READY, binding,
                Set.of(AttachmentOperation.PROBE_EXACT, AttachmentOperation.ABORT_INCOMPLETE), now.plusSeconds(60), "TEST"));
        AttachmentWorkerSupport support = new AttachmentWorkerSupport(workStore, mock(AttachmentFileRepository.class),
                mock(AttachmentTargetAccessRegistry.class), readiness, policy(), Clock.fixed(now, ZoneOffset.UTC));

        new AttachmentIncompleteAbortWorker(support, storage, mock(CommandServiceTemplate.class)).runOnce();

        verify(storage, never()).abortIncomplete(any(), any(), any(), anyInt(), any());
        verify(workStore).releaseJobForRetry(eq(fence), eq(AttachmentWorkStore.JobKind.INCOMPLETE_ABORT), any(),
                eq("PROBE_MISMATCH"), any(), anyInt(), any());
    }

    @Test
    void abortRaceWithForeignGenerationRetriesInsteadOfSettlingRemoved() throws Exception {
        Instant now = Instant.parse("2026-07-20T00:00:00Z");
        AttachmentWorkStore workStore = mock(AttachmentWorkStore.class);
        AttachmentStorage storage = mock(AttachmentStorage.class);
        StorageBindingRef binding = new StorageBindingRef(new byte[] {1});
        byte[] generation = new byte[32]; generation[0] = 7;
        AttachmentWorkStore.JobFence fence = new AttachmentWorkStore.JobFence(new TenantId(1L), new AttachmentFileId(2L),
                1, 3, 1, new byte[] {4}, 1);
        AttachmentWorkStore.LeasedJob job = new AttachmentWorkStore.LeasedJob(fence,
                AttachmentWorkStore.JobKind.INCOMPLETE_ABORT, now.plusSeconds(60), false, false);
        AttachmentWorkStore.ContentPlan plan = new AttachmentWorkStore.ContentPlan(new TenantId(1L), new AttachmentFileId(2L),
                1, 0, binding, new PlannedObjectLocator(new byte[43]), generation, 1, null, null, now.plusSeconds(60));
        when(workStore.claimDueJobs(eq(AttachmentWorkStore.JobKind.INCOMPLETE_ABORT), any(), any(), any(),
                anyInt(), anyInt(), any())).thenReturn(List.of(job));
        when(workStore.isLeaseCurrent(eq(fence), any())).thenReturn(true);
        when(workStore.heartbeat(eq(fence), any(), any())).thenReturn(true);
        when(workStore.findContentPlan(new TenantId(1L), new AttachmentFileId(2L))).thenReturn(Optional.of(plan));
        when(storage.probe(any())).thenReturn(new AttachmentStorage.ProbeResult(AttachmentStorage.ProbeState.ABSENT,
                null, null));
        when(storage.abortIncomplete(any(), any(), any(), anyLong(), any())).thenReturn(AttachmentStorage.AbortState.MISMATCH);
        AttachmentStorageReadinessResolver readiness = mock(AttachmentStorageReadinessResolver.class);
        when(readiness.resolve(any(), any())).thenReturn(new AttachmentStorageReadinessResolver.Snapshot(
                io.github.archetom.crm.application.port.out.attachment.AttachmentReadiness.READY, binding,
                Set.of(AttachmentOperation.PROBE_EXACT, AttachmentOperation.ABORT_INCOMPLETE), now.plusSeconds(60), "TEST"));
        AttachmentWorkerSupport support = new AttachmentWorkerSupport(workStore, mock(AttachmentFileRepository.class),
                mock(AttachmentTargetAccessRegistry.class), readiness, policy(), Clock.fixed(now, ZoneOffset.UTC));
        CommandServiceTemplate commandTemplate = mock(CommandServiceTemplate.class);

        new AttachmentIncompleteAbortWorker(support, storage, commandTemplate).runOnce();

        verify(workStore).releaseJobForRetry(eq(fence), eq(AttachmentWorkStore.JobKind.INCOMPLETE_ABORT), any(),
                eq("ABORT_MISMATCH"), any(), anyInt(), any());
        verify(commandTemplate, never()).execute(any(), any());
    }

    private static AttachmentRuntimePolicy policy() {
        Duration second = Duration.ofSeconds(1);
        return new AttachmentRuntimePolicy(true, 16, 64, 64, 2, 2, 2, 2, 128, 1,
                second, second, second, second, second, second, second, second,
                Duration.ofSeconds(5), Duration.ofMillis(20), 2, second, second, 1);
    }
}
