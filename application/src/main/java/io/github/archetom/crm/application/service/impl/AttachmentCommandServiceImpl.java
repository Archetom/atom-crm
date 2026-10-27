package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentInspectionReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorageReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccess;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccessRegistry;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetOperation;
import io.github.archetom.crm.application.service.AttachmentCommandService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.AttachmentUploadIntentVO;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.model.AttachmentByteState;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.MarketingActivityImageType;
import io.github.archetom.crm.domain.model.ProductImageType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class AttachmentCommandServiceImpl implements AttachmentCommandService {
    private final AttachmentFileRepository fileRepository;
    private final AttachmentWorkStore workStore;
    private final AttachmentStorage storage;
    private final AttachmentStorageReadinessResolver storageReadinessResolver;
    private final AttachmentInspectionReadinessResolver inspectionReadinessResolver;
    private final AttachmentRuntimePolicy runtimePolicy;
    private final OrganizationAccessGuard organizationAccessGuard;
    private final AttachmentTargetAccessRegistry targetAccessRegistry;
    private final AttachmentApplicationSupport support;
    private final CommandServiceTemplate commandTemplate;
    private final Clock clock;

    @Override
    public Result<AttachmentUploadIntentVO> createIntent(
            AuthenticatedCaller caller, String targetType, Long targetId,
            AttachmentUploadIntentCreateRequest request) {
        Result<IntentAdmission> admission = commandTemplate.execute(UseCaseOperation.ATTACHMENT_UPLOAD_INTENT_CREATE,
                new ServiceOperation<>() {
                    private AttachmentTarget target;
                    private TenantId tenantId;
                    private String displayName;
                    private String mediaType;
                    private MarketingActivityImageType marketingActivityImageType;
                    private ProductImageType productImageType;
                    private byte[] expectedSha;
                    private Instant now;
                    private AttachmentStorageReadinessResolver.Snapshot readiness;
                    private AttachmentInspectionReadinessResolver.Snapshot inspectionReadiness;

                    @Override
                    public void validate() {
                        target = support.target(targetType, targetId);
                        if (request == null || request.getExpectedBytes() == null) {
                            throw support.invalid("attachment upload intent is invalid");
                        }
                        tenantId = organizationAccessGuard.requireActiveMembership(caller,
                                AuthorityCatalog.FILES_WRITE);
                        displayName = support.displayName(request.getDisplayName());
                        mediaType = support.declaredMediaType(request.getDeclaredMediaType(), displayName);
                        support.requireMediaAllowedForTarget(target, mediaType);
                        marketingActivityImageType = support.marketingActivityImageType(target,
                                request.getMarketingActivityImageType(), mediaType);
                        productImageType = support.productImageType(target,
                                request.getProductImageType(), mediaType);
                        if (marketingActivityImageType != null && productImageType != null) {
                            throw support.invalid("attachment image groups are mutually exclusive");
                        }
                        expectedSha = support.expectedSha256(request.getExpectedSha256());
                        now = clock.instant();
                        if (!runtimePolicy.enabled() || request.getExpectedBytes() > runtimePolicy.maxFileBytes()) {
                            throw support.unavailable();
                        }
                        readiness = storageReadinessResolver.resolve(tenantId,
                                AttachmentOperation.ALLOCATE_WRITE);
                        inspectionReadiness = inspectionReadinessResolver.resolve(tenantId);
                        if (readiness == null || !readiness.isReady(AttachmentOperation.ALLOCATE_WRITE, now)
                                || !readiness.isReady(AttachmentOperation.STORE_SINGLE_STREAM, now)
                                || !readiness.isReady(AttachmentOperation.PROBE_EXACT, now)
                                || !readiness.isReady(AttachmentOperation.ABORT_INCOMPLETE, now)
                                || inspectionReadiness == null
                                || !inspectionReadiness.allows(mediaType, now)) {
                            throw support.unavailable();
                        }
                    }

                    @Override
                    public IntentAdmission execute() {
                        AttachmentTargetAccess targetAccess = targetAccess(target);
                        var lifecycle = targetAccess.lockForAttachmentMutation(caller, target,
                                AttachmentTargetOperation.CREATE_INTENT);
                        if (!tenantId.sameValueAs(lifecycle.tenantId()) || !lifecycle.attachmentsPermitted()) {
                            throw support.notFound();
                        }
                        if (target.type() == AttachmentTargetType.IMPORT_DRAFT
                                && !fileRepository.findActiveByTargetForUpdate(tenantId, target, 2).isEmpty()) {
                            throw support.invalid("CRM import drafts accept exactly one source file");
                        }
                        requireMarketingActivityImageCapacity(tenantId, target, marketingActivityImageType);
                        requireProductImageCapacity(tenantId, target, productImageType);
                        return new IntentAdmission(target, tenantId, displayName, mediaType, expectedSha,
                                request.getExpectedBytes(), marketingActivityImageType, productImageType, readiness, now);
                    }
                });
        if (!admission.isSuccess()) return copyFailure(admission);

        AttachmentStorage.Allocation allocation;
        IntentAdmission subject = admission.getData();
        Instant planDeadline = subject.now().plus(runtimePolicy.intentTtl());
        try {
            // This is deliberately after the first target-root authorization transaction.
            allocation = storage.allocate(subject.readiness().binding(), subject.expectedBytes(), planDeadline);
        } catch (Exception exception) {
            return unavailable();
        }

        Result<AttachmentUploadIntentVO> created = commandTemplate.execute(
                UseCaseOperation.ATTACHMENT_UPLOAD_INTENT_CREATE, new ServiceOperation<>() {
                    @Override public void validate() { }
                    @Override public AttachmentUploadIntentVO execute() {
                        var lifecycle = targetAccess(subject.target()).lockForAttachmentMutation(caller,
                                subject.target(), AttachmentTargetOperation.CREATE_INTENT);
                        if (!subject.tenantId().sameValueAs(lifecycle.tenantId()) || !lifecycle.attachmentsPermitted()) {
                            throw support.notFound();
                        }
                        requireMarketingActivityImageCapacity(subject.tenantId(), subject.target(),
                                subject.marketingActivityImageType());
                        requireProductImageCapacity(subject.tenantId(), subject.target(),
                                subject.productImageType());
                        AttachmentFile file = AttachmentFile.createIntent(subject.tenantId(),
                                new MemberId(caller.actorId()), subject.displayName(), subject.mediaType(),
                                subject.expectedBytes(), subject.expectedSha(), local(subject.now()));
                        fileRepository.saveIntent(subject.tenantId(), subject.target(), file,
                                subject.marketingActivityImageType(), subject.productImageType());
                        if (!workStore.reserveUsage(subject.tenantId(), new AttachmentWorkStore.UsageDelta(
                                0, 0, subject.expectedBytes(), 1, 1, 0, 0),
                                runtimePolicy.usageLimits(), subject.now())) {
                            throw support.unavailable();
                        }
                        workStore.createContentPlan(subject.tenantId(), file.getId(), file.getContentVersion(), file.getVersion(),
                                subject.readiness().binding(), allocation.locator(), allocation.storageGeneration(),
                                1, planDeadline);
                        Instant jobDeadline = planDeadline.plus(runtimePolicy.tombstoneRetention());
                        workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.tenantId(), file.getId(),
                                file.getContentVersion(), AttachmentWorkStore.JobKind.UPLOAD_RECONCILIATION,
                                1, planDeadline, jobDeadline));
                        workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.tenantId(), file.getId(),
                                file.getContentVersion(), AttachmentWorkStore.JobKind.RETENTION_SWEEP,
                                1, planDeadline, jobDeadline));
                        return new AttachmentUploadIntentVO(file.getId().getValue(), file.getDisplayName(),
                                file.getDeclaredMediaType(), file.getExpectedBytes(), "UPLOADING",
                                file.getVersion(), local(planDeadline), null);
                    }
                });
        if (!created.isSuccess()) {
            try { storage.abortIncomplete(subject.readiness().binding(), allocation.locator(),
                    allocation.storageGeneration(), 1, planDeadline); }
            catch (Exception ignored) { /* the reconciliation-less allocation is safely unreferenced */ }
        }
        return created;
    }

    @Override
    public Result<Void> withdraw(AuthenticatedCaller caller, String targetType, Long targetId,
                                 Long fileIdValue, Long version) {
        return commandTemplate.execute(UseCaseOperation.ATTACHMENT_WITHDRAW, new ServiceOperation<>() {
            private AttachmentTarget target;
            private AttachmentFileId fileId;
            private TenantId tenantId;
            private Instant now;

            @Override
            public void validate() {
                target = support.target(targetType, targetId);
                fileId = support.fileId(fileIdValue);
                if (version == null || version < 0) {
                    throw support.invalid("attachment version is invalid");
                }
                tenantId = organizationAccessGuard.requireActiveMembership(caller,
                        AuthorityCatalog.FILES_WRITE);
                now = clock.instant();
            }

            @Override
            public Void execute() {
                var lifecycle = targetAccess(target).lockForAttachmentMutation(caller, target,
                        AttachmentTargetOperation.WITHDRAW);
                if (!tenantId.sameValueAs(lifecycle.tenantId()) || !lifecycle.attachmentsPermitted()) {
                    throw support.notFound();
                }
                AttachmentFile file = fileRepository.findByTargetForUpdate(tenantId, target, fileId)
                        .orElseThrow(support::notFound);
                if (!version.equals(file.getVersion())) {
                    throw support.invalid("attachment version is invalid");
                }
                file.withdraw(local(now));
                fileRepository.withdrawRelation(tenantId, target, fileId);
                fileRepository.save(tenantId, file);
                AttachmentWorkStore.JobKind kind = file.getByteState() == AttachmentByteState.REMOVING
                        ? AttachmentWorkStore.JobKind.DELETION
                        : AttachmentWorkStore.JobKind.INCOMPLETE_ABORT;
                workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, fileId,
                        file.getContentVersion(), kind, 1, now, now.plus(runtimePolicy.tombstoneRetention())));
                Instant retentionAt = now.plus(runtimePolicy.tombstoneRetention());
                workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, fileId,
                        file.getContentVersion(), AttachmentWorkStore.JobKind.RETENTION_SWEEP,
                        3, retentionAt, retentionAt.plus(runtimePolicy.tombstoneRetention())));
                return null;
            }
        });
    }

    private AttachmentTargetAccess targetAccess(AttachmentTarget target) {
        try {
            return targetAccessRegistry.require(target.type());
        } catch (UnsupportedOperationException exception) {
            throw support.invalid("attachment target is unsupported");
        }
    }

    private void requireMarketingActivityImageCapacity(
            TenantId tenantId, AttachmentTarget target, MarketingActivityImageType marketingActivityImageType) {
        if (marketingActivityImageType == null) return;
        if (fileRepository.countActiveMarketingActivityImagesForUpdate(tenantId, target,
                marketingActivityImageType) >= 9) {
            throw support.marketingActivityImageLimit();
        }
    }

    private void requireProductImageCapacity(
            TenantId tenantId, AttachmentTarget target, ProductImageType productImageType) {
        if (productImageType == null) return;
        if (fileRepository.countActiveProductImagesForUpdate(tenantId, target, productImageType) >= 9) {
            throw support.productImageLimit();
        }
    }

    private static LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
                .withNano(instant.getNano() / 1_000 * 1_000);
    }

    private <T> Result<T> unavailable() {
        return commandTemplate.execute(UseCaseOperation.ATTACHMENT_UPLOAD_INTENT_CREATE,
                new ServiceOperation<>() {
                    @Override public void validate() { throw support.unavailable(); }
                    @Override public T execute() { throw new IllegalStateException(); }
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> Result<T> copyFailure(Result<?> result) { return (Result<T>) result; }

    private record IntentAdmission(AttachmentTarget target, TenantId tenantId, String displayName,
                                   String mediaType, byte[] expectedSha, long expectedBytes,
                                   MarketingActivityImageType marketingActivityImageType,
                                   ProductImageType productImageType,
                                   AttachmentStorageReadinessResolver.Snapshot readiness, Instant now) {
        private IntentAdmission {
            expectedSha = expectedSha == null ? null : expectedSha.clone();
        }
        @Override public byte[] expectedSha() {
            return expectedSha == null ? null : expectedSha.clone();
        }
    }
}
