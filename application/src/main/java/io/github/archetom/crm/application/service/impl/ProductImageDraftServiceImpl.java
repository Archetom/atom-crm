package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.port.out.attachment.AttachmentInspectionReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorageReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.ProductImageDraftService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.AttachmentUploadIntentVO;
import io.github.archetom.crm.application.vo.ProductImageDraftVO;
import io.github.archetom.crm.application.vo.ProductImageUploadStatusVO;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.ProductImageDraft;
import io.github.archetom.crm.domain.model.AttachmentByteState;
import io.github.archetom.crm.domain.model.AttachmentInspectionState;
import io.github.archetom.crm.domain.model.AttachmentPublicationState;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.ProductImageType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.ProductImageDraftRepository;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ProductImageDraftId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Authenticated Product image staging. Staged files gain a Product relation only on create. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageDraftServiceImpl implements ProductImageDraftService {
    private static final int MAX_IMAGES_PER_GROUP = 9;

    private final ProductImageDraftRepository draftRepository;
    private final AttachmentFileRepository fileRepository;
    private final AttachmentWorkStore workStore;
    private final AttachmentStorage storage;
    private final AttachmentStorageReadinessResolver storageReadinessResolver;
    private final AttachmentInspectionReadinessResolver inspectionReadinessResolver;
    private final AttachmentRuntimePolicy runtimePolicy;
    private final OrganizationAccessGuard accessGuard;
    private final AttachmentApplicationSupport support;
    private final CommandServiceTemplate commandTemplate;
    private final Clock clock;

    @Override
    public Result<ProductImageDraftVO> create(AuthenticatedCaller caller) {
        return commandTemplate.execute(UseCaseOperation.ATTACHMENT_UPLOAD_INTENT_CREATE, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId memberId;
            @Override public void validate() {
                tenantId = requireProductImageWrite(caller);
                memberId = new MemberId(caller.actorId());
            }
            @Override public ProductImageDraftVO execute() {
                Instant now = clock.instant();
                ProductImageDraft draft = ProductImageDraft.create(tenantId, memberId,
                        local(now.plus(runtimePolicy.intentTtl())), local(now));
                draftRepository.save(tenantId, draft);
                return draftVO(draft);
            }
        });
    }

    @Override
    public Result<AttachmentUploadIntentVO> createIntent(AuthenticatedCaller caller, Long rawDraftId,
                                                           String rawImageType,
                                                           AttachmentUploadIntentCreateRequest request) {
        Result<IntentAdmission> admitted = commandTemplate.execute(UseCaseOperation.ATTACHMENT_UPLOAD_INTENT_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId; private MemberId memberId; private ProductImageDraftId draftId;
                    private ProductImageType imageType; private String name; private String mediaType;
                    private byte[] expectedSha; private Instant now;
                    private AttachmentStorageReadinessResolver.Snapshot readiness;
                    @Override public void validate() {
                        if (request == null || request.getExpectedBytes() == null
                                || request.getMarketingActivityImageType() != null
                                || request.getProductImageType() != null) {
                            throw invalid("product image upload intent is invalid");
                        }
                        tenantId = requireProductImageWrite(caller); memberId = new MemberId(caller.actorId());
                        draftId = draftId(rawDraftId); imageType = imageType(rawImageType);
                        name = support.displayName(request.getDisplayName());
                        mediaType = support.declaredMediaType(request.getDeclaredMediaType(), name);
                        requireImageMedia(mediaType); expectedSha = support.expectedSha256(request.getExpectedSha256());
                        now = clock.instant();
                        if (!runtimePolicy.enabled() || request.getExpectedBytes() > runtimePolicy.maxFileBytes()) {
                            throw support.unavailable();
                        }
                        readiness = storageReadinessResolver.resolve(tenantId, AttachmentOperation.ALLOCATE_WRITE);
                        var inspection = inspectionReadinessResolver.resolve(tenantId);
                        if (!ready(readiness, AttachmentOperation.ALLOCATE_WRITE, now)
                                || !ready(readiness, AttachmentOperation.STORE_SINGLE_STREAM, now)
                                || !ready(readiness, AttachmentOperation.PROBE_EXACT, now)
                                || !ready(readiness, AttachmentOperation.ABORT_INCOMPLETE, now)
                                || inspection == null || !inspection.allows(mediaType, now)) {
                            throw support.unavailable();
                        }
                    }
                    @Override public IntentAdmission execute() {
                        ProductImageDraft draft = activeDraft(tenantId, draftId, memberId, true);
                        long count = draftRepository.findStagedFilesForUpdate(tenantId, draft.getId()).stream()
                                .filter(value -> value.imageType() == imageType).count();
                        if (count >= MAX_IMAGES_PER_GROUP) throw support.productImageLimit();
                        return new IntentAdmission(tenantId, memberId, draft.getId(), imageType, name, mediaType,
                                expectedSha, request.getExpectedBytes(), readiness, now);
                    }
                });
        if (!admitted.isSuccess()) return copyFailure(admitted);
        IntentAdmission subject = admitted.getData();
        Instant deadline = subject.now().plus(runtimePolicy.intentTtl());
        AttachmentStorage.Allocation allocation;
        try { allocation = storage.allocate(subject.readiness().binding(), subject.expectedBytes(), deadline); }
        catch (Exception exception) { return unavailable(); }
        Result<AttachmentUploadIntentVO> created = commandTemplate.execute(UseCaseOperation.ATTACHMENT_UPLOAD_INTENT_CREATE,
                new ServiceOperation<>() {
                    @Override public void validate() { }
                    @Override public AttachmentUploadIntentVO execute() {
                        ProductImageDraft draft = activeDraft(subject.tenantId(), subject.draftId(),
                                subject.memberId(), true);
                        long ordinal = draftRepository.findStagedFilesForUpdate(subject.tenantId(), draft.getId()).stream()
                                .filter(value -> value.imageType() == subject.imageType()).count();
                        if (ordinal >= MAX_IMAGES_PER_GROUP) throw support.productImageLimit();
                        AttachmentFile file = AttachmentFile.createIntent(subject.tenantId(), subject.memberId(),
                                subject.name(), subject.mediaType(), subject.expectedBytes(), subject.expectedSha(),
                                local(subject.now()));
                        fileRepository.saveStagedIntent(subject.tenantId(), file);
                        if (!workStore.reserveUsage(subject.tenantId(), new AttachmentWorkStore.UsageDelta(
                                0, 0, subject.expectedBytes(), 1, 1, 0, 0), runtimePolicy.usageLimits(), subject.now())) {
                            throw support.unavailable();
                        }
                        draftRepository.stage(subject.tenantId(), draft.getId(), subject.imageType(), file.getId(),
                                Math.toIntExact(ordinal));
                        workStore.createContentPlan(subject.tenantId(), file.getId(), file.getContentVersion(),
                                file.getVersion(), subject.readiness().binding(), allocation.locator(),
                                allocation.storageGeneration(), 1, deadline);
                        Instant cleanupDeadline = deadline.plus(runtimePolicy.tombstoneRetention());
                        workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.tenantId(), file.getId(),
                                file.getContentVersion(), AttachmentWorkStore.JobKind.UPLOAD_RECONCILIATION,
                                1, deadline, cleanupDeadline));
                        workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.tenantId(), file.getId(),
                                file.getContentVersion(), AttachmentWorkStore.JobKind.RETENTION_SWEEP,
                                1, draft.getDeadline().toInstant(ZoneOffset.UTC), cleanupDeadline));
                        return new AttachmentUploadIntentVO(file.getId().getValue(), file.getDisplayName(),
                                file.getDeclaredMediaType(), file.getExpectedBytes(), "UPLOADING", file.getVersion(),
                                local(deadline), draft.getId().getValue());
                    }
                });
        if (!created.isSuccess()) {
            try {
                storage.abortIncomplete(subject.readiness().binding(), allocation.locator(),
                        allocation.storageGeneration(), 1, deadline);
            } catch (Exception cleanupFailure) {
                // The upload already failed; a failed cleanup must not replace that result. The
                // reconcile worker still owns the orphan, so record only that it was left behind.
                log.warn("Incomplete upload cleanup failed: exceptionType={}",
                        cleanupFailure.getClass().getName());
            }
        }
        return created;
    }

    @Override
    public Result<ProductImageUploadStatusVO> upload(AuthenticatedCaller caller, Long rawDraftId,
                                                       String rawImageType, Long rawFileId, Long version,
                                                       AttachmentByteSource source) {
        Result<UploadSubject> admitted = commandTemplate.execute(UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD,
                new ServiceOperation<>() {
                    private TenantId tenantId; private MemberId memberId; private ProductImageDraftId draftId;
                    private ProductImageType imageType; private AttachmentFileId fileId;
                    @Override public void validate() {
                        tenantId = requireProductImageWrite(caller); memberId = new MemberId(caller.actorId());
                        draftId = draftId(rawDraftId); imageType = imageType(rawImageType); fileId = fileId(rawFileId);
                        if (source == null || version == null || version < 0) throw invalid("product image upload is invalid");
                    }
                    @Override public UploadSubject execute() {
                        ProductImageDraft draft = activeDraft(tenantId, draftId, memberId, true);
                        ProductImageDraftRepository.StagedFile staged = staged(tenantId, draft, imageType, fileId);
                        AttachmentFile file = staged.file();
                        if (!file.isUploadedBy(memberId) || source.declaredLength() != file.getExpectedBytes()) {
                            throw support.notFound();
                        }
                        AttachmentWorkStore.ContentPlan plan = workStore.findContentPlan(tenantId, fileId)
                                .orElseThrow(support::notFound);
                        var readiness = storageReadinessResolver.resolve(tenantId, AttachmentOperation.STORE_SINGLE_STREAM);
                        if (!ready(readiness, AttachmentOperation.STORE_SINGLE_STREAM, clock.instant())
                                || !Arrays.equals(readiness.binding().copyValue(), plan.binding().copyValue())) {
                            throw support.unavailable();
                        }
                        if (file.getByteState() == AttachmentByteState.STORED) {
                            if (!version.equals(plan.intentVersion()) || plan.evidence() == null || plan.storedIdentity() == null) {
                                throw support.conflict();
                            }
                        } else if (!version.equals(file.getVersion()) || file.getByteState() != AttachmentByteState.UPLOADING
                                || file.getPublicationState() != AttachmentPublicationState.PRIVATE
                                || !clock.instant().isBefore(plan.deadline())) {
                            throw support.notFound();
                        }
                        return new UploadSubject(tenantId, memberId, draft.getId(), imageType, fileId, file, plan, readiness);
                    }
                });
        if (!admitted.isSuccess()) return copyFailure(admitted);
        UploadSubject subject = admitted.getData();
        AttachmentStorage.StoreReceipt receipt;
        try {
            receipt = storage.store(new AttachmentStorage.StoreCommand(subject.plan().binding(), subject.plan().locator(),
                    subject.plan().storageGeneration(), subject.file().getContentVersion(), subject.plan().attempt(),
                    subject.file().getExpectedBytes(), subject.file().getExpectedSha256(), uploadDeadline(subject.plan()),
                    runtimePolicy.uploadOpenTimeout(), runtimePolicy.uploadIdleTimeout()), source);
        } catch (AttachmentStorage.StoreConflictException exception) { return conflict(); }
        catch (Exception exception) { return unavailable(); }
        if (receipt == null || receipt.identity() == null || receipt.evidence() == null) return unavailable();
        return commandTemplate.execute(UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD, new ServiceOperation<>() {
            @Override public void validate() { }
            @Override public ProductImageUploadStatusVO execute() {
                ProductImageDraft draft = activeDraft(subject.tenantId(), subject.draftId(), subject.memberId(), true);
                AttachmentFile file = staged(subject.tenantId(), draft, subject.imageType(), subject.fileId()).file();
                AttachmentWorkStore.ContentPlan current = workStore.findContentPlan(subject.tenantId(), subject.fileId())
                        .orElseThrow(support::notFound);
                if (!version.equals(current.intentVersion()) || current.contentVersion() != file.getContentVersion()
                        || !Arrays.equals(current.storageGeneration(), subject.plan().storageGeneration())) throw support.conflict();
                if (file.getByteState() == AttachmentByteState.STORED) {
                    if (current.evidence() == null || current.storedIdentity() == null
                            || current.evidence().actualBytes() != receipt.evidence().actualBytes()
                            || !Arrays.equals(current.evidence().sha256(), receipt.evidence().sha256())
                            || !Arrays.equals(current.storedIdentity().copyValue(), receipt.identity().copyValue())) {
                        throw support.conflict();
                    }
                    return status(draft, subject.imageType(), file);
                }
                if (file.getByteState() != AttachmentByteState.UPLOADING
                        || file.getPublicationState() != AttachmentPublicationState.PRIVATE) throw support.notFound();
                file.acceptStored(receipt.evidence(), file.getDeclaredMediaType(), local(clock.instant()));
                fileRepository.save(subject.tenantId(), file);
                workStore.recordStored(subject.tenantId(), subject.fileId(), file.getContentVersion(), current.attempt(),
                        receipt.identity(), receipt.evidence(), clock.instant());
                if (!workStore.reserveUsage(subject.tenantId(), new AttachmentWorkStore.UsageDelta(
                        0, receipt.evidence().actualBytes(), -file.getExpectedBytes(), -1, -1, 1, 0),
                        runtimePolicy.usageLimits(), clock.instant())) throw support.unavailable();
                workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.tenantId(), subject.fileId(),
                        file.getContentVersion(), AttachmentWorkStore.JobKind.INSPECTION, 1, clock.instant(),
                        clock.instant().plus(runtimePolicy.inspectionTotalTimeout())));
                return status(draft, subject.imageType(), file);
            }
        });
    }

    @Override
    public Result<ProductImageUploadStatusVO> uploadStatus(AuthenticatedCaller caller, Long rawDraftId,
                                                             String rawImageType, Long rawFileId) {
        return commandTemplate.execute(UseCaseOperation.ATTACHMENT_UPLOAD_STATUS_GET, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId memberId; private ProductImageDraftId draftId;
            private ProductImageType imageType; private AttachmentFileId fileId;
            @Override public void validate() {
                tenantId = requireProductImageWrite(caller); memberId = new MemberId(caller.actorId());
                draftId = draftId(rawDraftId); imageType = imageType(rawImageType); fileId = fileId(rawFileId);
            }
            @Override public ProductImageUploadStatusVO execute() {
                ProductImageDraft draft = activeDraft(tenantId, draftId, memberId, true);
                AttachmentFile file = staged(tenantId, draft, imageType, fileId).file();
                if (!file.isUploadedBy(memberId)) throw support.notFound();
                return status(draft, imageType, file);
            }
        });
    }

    @Override
    public Result<Void> withdraw(AuthenticatedCaller caller, Long rawDraftId, Long version) {
        return commandTemplate.execute(UseCaseOperation.ATTACHMENT_WITHDRAW, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId memberId; private ProductImageDraftId draftId;
            @Override public void validate() {
                tenantId = requireProductImageWrite(caller); memberId = new MemberId(caller.actorId());
                draftId = draftId(rawDraftId);
                if (version == null || version < 0) throw invalid("product image draft version is invalid");
            }
            @Override public Void execute() {
                ProductImageDraft draft = activeDraft(tenantId, draftId, memberId, true);
                if (!version.equals(draft.getVersion())) throw support.conflict();
                List<ProductImageDraftRepository.StagedFile> files = draftRepository.findStagedFilesForUpdate(tenantId,
                        draft.getId());
                draft.withdraw(memberId, local(clock.instant()));
                draftRepository.withdrawStagedFiles(tenantId, draft.getId());
                cleanup(tenantId, files, clock.instant());
                draftRepository.save(tenantId, draft);
                return null;
            }
        });
    }

    @Override
    public void consumeCreateDraft(TenantId tenantId, MemberId uploader, Long rawDraftId,
                                   Map<String, List<Long>> images, AttachmentTarget target) {
        Map<ProductImageType, Set<AttachmentFileId>> requested = requested(images);
        if (rawDraftId == null) {
            if (!requested.isEmpty()) throw invalid("product image draft is required");
            return;
        }
        if (target == null || target.type() != AttachmentTargetType.PRODUCT) {
            throw invalid("product image target is invalid");
        }
        ProductImageDraft draft = activeDraft(tenantId, draftId(rawDraftId), uploader, true);
        List<ProductImageDraftRepository.StagedFile> staged = draftRepository.findStagedFilesForUpdate(tenantId,
                draft.getId());
        Map<ProductImageType, Set<AttachmentFileId>> actual = staged.stream().collect(Collectors.groupingBy(
                ProductImageDraftRepository.StagedFile::imageType, () -> new EnumMap<>(ProductImageType.class),
                Collectors.mapping(value -> value.file().getId(), Collectors.toSet())));
        if (staged.isEmpty() || !actual.equals(requested)) {
            throw invalid("product image draft must be consumed as an exact set");
        }
        LocalDateTime now = local(clock.instant());
        for (ProductImageDraftRepository.StagedFile candidate : staged) {
            AttachmentFile file = candidate.file();
            if (!file.isUploadedBy(uploader)) throw invalid("product image candidate is unavailable");
            file.publish(now);
            fileRepository.save(tenantId, file);
            fileRepository.bindStagedProductImage(tenantId, target, file.getId(), candidate.imageType());
        }
        draftRepository.markStagedBound(tenantId, draft.getId(), staged);
        draft.consume(uploader, now);
        draftRepository.save(tenantId, draft);
    }

    private ProductImageDraft activeDraft(TenantId tenantId, ProductImageDraftId draftId, MemberId memberId,
                                          boolean forUpdate) {
        ProductImageDraft draft = (forUpdate ? draftRepository.findForUpdate(tenantId, draftId)
                : draftRepository.find(tenantId, draftId)).orElseThrow(support::notFound);
        if (draft.expire(local(clock.instant()))) {
            List<ProductImageDraftRepository.StagedFile> staged = draftRepository.findStagedFilesForUpdate(tenantId,
                    draft.getId());
            draftRepository.withdrawStagedFiles(tenantId, draft.getId());
            cleanup(tenantId, staged, clock.instant());
            draftRepository.save(tenantId, draft);
            throw support.notFound();
        }
        if (!draft.isActiveFor(memberId, local(clock.instant()))) throw support.notFound();
        return draft;
    }

    private ProductImageDraftRepository.StagedFile staged(TenantId tenantId, ProductImageDraft draft,
                                                           ProductImageType imageType, AttachmentFileId fileId) {
        return draftRepository.findStagedFilesForUpdate(tenantId, draft.getId()).stream()
                .filter(value -> value.imageType() == imageType && value.file().getId().sameValueAs(fileId))
                .findFirst().orElseThrow(support::notFound);
    }

    private Map<ProductImageType, Set<AttachmentFileId>> requested(Map<String, List<Long>> source) {
        if (source == null || source.isEmpty()) return Map.of();
        EnumMap<ProductImageType, Set<AttachmentFileId>> result = new EnumMap<>(ProductImageType.class);
        for (Map.Entry<String, List<Long>> entry : source.entrySet()) {
            ProductImageType type = imageType(entry.getKey());
            if (entry.getValue() == null || entry.getValue().isEmpty() || entry.getValue().size() > MAX_IMAGES_PER_GROUP
                    || result.containsKey(type)) throw invalid("product images are invalid");
            Set<AttachmentFileId> ids = new HashSet<>();
            for (Long rawId : entry.getValue()) {
                AttachmentFileId id = fileId(rawId);
                if (!ids.add(id)) throw invalid("product image candidate is duplicated");
            }
            result.put(type, Set.copyOf(ids));
        }
        return Map.copyOf(result);
    }

    private void cleanup(TenantId tenantId, Collection<ProductImageDraftRepository.StagedFile> staged,
                         Instant now) {
        for (ProductImageDraftRepository.StagedFile candidate : staged) {
            AttachmentFile file = candidate.file();
            file.withdraw(local(now)); fileRepository.save(tenantId, file);
            AttachmentWorkStore.JobKind kind = file.getByteState() == AttachmentByteState.REMOVING
                    ? AttachmentWorkStore.JobKind.DELETION : AttachmentWorkStore.JobKind.INCOMPLETE_ABORT;
            Instant deadline = now.plus(runtimePolicy.tombstoneRetention());
            workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, file.getId(),
                    file.getContentVersion(), kind, 1, now, deadline));
            workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, file.getId(),
                    file.getContentVersion(), AttachmentWorkStore.JobKind.RETENTION_SWEEP,
                    3, deadline, deadline.plus(runtimePolicy.tombstoneRetention())));
        }
    }

    private TenantId requireProductImageWrite(AuthenticatedCaller caller) {
        TenantId tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.PRODUCTS_WRITE);
        accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
        return tenantId;
    }
    private static boolean ready(AttachmentStorageReadinessResolver.Snapshot value,
                                 AttachmentOperation operation, Instant now) {
        return value != null && value.isReady(operation, now);
    }
    private static void requireImageMedia(String value) {
        if (!"image/png".equals(value) && !"image/jpeg".equals(value)) throw invalid("product images must be PNG or JPEG");
    }
    private Instant uploadDeadline(AttachmentWorkStore.ContentPlan plan) {
        Instant total = clock.instant().plus(runtimePolicy.uploadTotalTimeout());
        return plan.deadline().isBefore(total) ? plan.deadline() : total;
    }
    private static ProductImageDraftVO draftVO(ProductImageDraft value) {
        return new ProductImageDraftVO(value.getId().getValue(), value.getVersion(), value.getDeadline());
    }
    private static ProductImageUploadStatusVO status(ProductImageDraft draft, ProductImageType imageType,
                                                      AttachmentFile file) {
        String status = file.getInspectionState() == AttachmentInspectionState.REJECTED
                || file.getPublicationState() == AttachmentPublicationState.WITHDRAWN
                || file.getPublicationState() == AttachmentPublicationState.DELETED
                || file.getByteState() == AttachmentByteState.REMOVING || file.getByteState() == AttachmentByteState.REMOVED
                ? "FAILED" : file.getByteState() == AttachmentByteState.STORED
                && file.getInspectionState() == AttachmentInspectionState.CLEAN ? "UPLOADED" : "PENDING";
        return new ProductImageUploadStatusVO(draft.getId().getValue(), imageType.name(), file.getId().getValue(),
                file.getDisplayName(), file.getMediaType() == null ? file.getDeclaredMediaType() : file.getMediaType(),
                file.getActualBytes() > 0 ? file.getActualBytes() : file.getExpectedBytes(), status);
    }
    private static ProductImageDraftId draftId(Long value) {
        try { return new ProductImageDraftId(value); }
        catch (RuntimeException exception) { throw invalid("product image draft ID is invalid"); }
    }
    private static AttachmentFileId fileId(Long value) {
        try { return new AttachmentFileId(value); }
        catch (RuntimeException exception) { throw invalid("product image file ID is invalid"); }
    }
    private static ProductImageType imageType(String value) {
        try { return ProductImageType.valueOf(value); }
        catch (RuntimeException exception) { throw invalid("product image type is invalid"); }
    }
    private static LocalDateTime local(Instant time) { return LocalDateTime.ofInstant(time, ZoneOffset.UTC); }
    private <T> Result<T> unavailable() { return failure(support.unavailable()); }
    private <T> Result<T> conflict() { return failure(support.conflict()); }
    private <T> Result<T> failure(RuntimeException exception) {
        return commandTemplate.execute(UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD, new ServiceOperation<>() {
            @Override public void validate() { throw exception; }
            @Override public T execute() { throw new IllegalStateException(); }
        });
    }
    @SuppressWarnings("unchecked") private static <T> Result<T> copyFailure(Result<?> result) { return (Result<T>) result; }
    private static NonRetryableApplicationException invalid(String value) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, value);
    }
    private record IntentAdmission(TenantId tenantId, MemberId memberId, ProductImageDraftId draftId,
                                   ProductImageType imageType, String name, String mediaType, byte[] expectedSha,
                                   long expectedBytes, AttachmentStorageReadinessResolver.Snapshot readiness, Instant now) {
        private IntentAdmission { expectedSha = expectedSha == null ? null : expectedSha.clone(); }
        @Override public byte[] expectedSha() { return expectedSha == null ? null : expectedSha.clone(); }
    }
    private record UploadSubject(TenantId tenantId, MemberId memberId, ProductImageDraftId draftId,
                                 ProductImageType imageType, AttachmentFileId fileId, AttachmentFile file,
                                 AttachmentWorkStore.ContentPlan plan,
                                 AttachmentStorageReadinessResolver.Snapshot readiness) { }
}
