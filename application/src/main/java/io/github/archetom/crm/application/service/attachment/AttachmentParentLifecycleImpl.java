package io.github.archetom.crm.application.service.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.model.AttachmentByteState;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Parent-owned, transaction-local withdrawal. Callers already hold the exact CRM root lock;
 * this component only closes relations and persists durable cleanup work, never storage effects.
 */
@Component
@RequiredArgsConstructor
public class AttachmentParentLifecycleImpl implements AttachmentParentLifecycle {
    private final AttachmentFileRepository fileRepository;
    private final AttachmentWorkStore workStore;
    private final AttachmentRuntimePolicy runtimePolicy;
    private final Clock clock;

    @Override
    public void withdrawAll(TenantId tenantId, AttachmentTarget target) {
        if (tenantId == null || target == null) {
            throw new IllegalArgumentException("attachment parent lifecycle scope is required");
        }
        Instant now = clock.instant();
        LocalDateTime localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC)
                .withNano(now.getNano() / 1_000 * 1_000);
        for (AttachmentFile file : fileRepository.findActiveByTargetForUpdate(tenantId, target)) {
            withdraw(tenantId, target, file, now, localNow);
        }
    }

    @Override
    public void cutoffNonAvailable(TenantId tenantId, AttachmentTarget target) {
        if (tenantId == null || target == null) {
            throw new IllegalArgumentException("attachment parent lifecycle scope is required");
        }
        // The generic target cap is 100; request 101 rows so a broken database invariant can
        // never leave a partially frozen terminal request behind.
        var files = fileRepository.findActiveByTargetForUpdate(tenantId, target, 101);
        if (files.size() > 100) {
            throw new IllegalStateException("attachment target capacity is invalid");
        }
        Instant now = clock.instant();
        LocalDateTime localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC)
                .withNano(now.getNano() / 1_000 * 1_000);
        for (AttachmentFile file : files) {
            if (!file.isAvailable()) {
                withdraw(tenantId, target, file, now, localNow);
            }
        }
    }

    @Override
    public void resumeAll(TenantId tenantId, AttachmentTarget target) {
        if (tenantId == null || target == null) {
            throw new IllegalArgumentException("attachment parent lifecycle scope is required");
        }
        // A disabled profile can neither have workers nor create fresh attachment work. Existing
        // relations remain untouched until the profile is re-enabled, at which point normal
        // parent transitions can rearm paused work with a complete runtime policy.
        if (!runtimePolicy.enabled()) return;
        Instant now = clock.instant();
        Instant deadline = now.plus(runtimePolicy.tombstoneRetention());
        for (AttachmentFile file : fileRepository.findActiveByTargetForUpdate(tenantId, target)) {
            workStore.resumePausedJobs(tenantId, file.getId(), now, deadline, now);
        }
    }

    private void withdraw(TenantId tenantId, AttachmentTarget target, AttachmentFile file,
                          Instant now, LocalDateTime localNow) {
        file.withdraw(localNow);
        fileRepository.withdrawRelation(tenantId, target, file.getId());
        fileRepository.save(tenantId, file);
        // A disabled profile has no validated cleanup deadline and no worker able to consume
        // durable jobs. The relation must still close atomically with its parent transition.
        if (!runtimePolicy.enabled()) return;
        AttachmentWorkStore.JobKind kind = file.getByteState() == AttachmentByteState.REMOVING
                ? AttachmentWorkStore.JobKind.DELETION
                : AttachmentWorkStore.JobKind.INCOMPLETE_ABORT;
        Instant deadline = now.plus(runtimePolicy.tombstoneRetention());
        workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, file.getId(),
                file.getContentVersion(), kind, 1, now, deadline));
        workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, file.getId(),
                file.getContentVersion(), AttachmentWorkStore.JobKind.RETENTION_SWEEP,
                3, deadline, deadline.plus(runtimePolicy.tombstoneRetention())));
    }
}
