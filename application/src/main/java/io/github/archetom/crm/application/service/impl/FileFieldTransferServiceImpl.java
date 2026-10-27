package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.port.out.attachment.AttachmentOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorageReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.PreCreateFileFieldDraftAuthorizer;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccessRegistry;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetOperation;
import io.github.archetom.crm.application.service.FileFieldTransferService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.FileFieldUploadedFileVO;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.FileFieldDraft;
import io.github.archetom.crm.domain.model.AttachmentByteState;
import io.github.archetom.crm.domain.model.AttachmentInspectionState;
import io.github.archetom.crm.domain.model.AttachmentPublicationState;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FileFieldDraftId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

/** Mirrors B24a's stream/finalize split without manufacturing an ordinary attachment relation. */
@Service
@RequiredArgsConstructor
public class FileFieldTransferServiceImpl implements FileFieldTransferService {
    private final FileFieldRepository draftRepository;
    private final AttachmentFileRepository fileRepository;
    private final AttachmentWorkStore workStore;
    private final AttachmentStorage storage;
    private final AttachmentStorageReadinessResolver readinessResolver;
    private final AttachmentRuntimePolicy policy;
    private final OrganizationAccessGuard accessGuard;
    private final PreCreateFileFieldDraftAuthorizer preCreateDraftAuthorizer;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final AttachmentTargetAccessRegistry targetAccessRegistry;
    private final AttachmentApplicationSupport support;
    private final CommandServiceTemplate commandTemplate;
    private final Clock clock;

    @Override
    public Result<FileFieldUploadedFileVO> upload(AuthenticatedCaller caller, Long rawDraftId,
                                                   String rawFieldCode, Long rawFileId, Long version,
                                                   AttachmentByteSource source) {
        Result<Subject> admitted = commandTemplate.execute(UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD,
                new ServiceOperation<>() {
                    private TenantId tenantId; private MemberId memberId; private FileFieldDraftId draftId;
                    private AttachmentFileId fileId; private FieldCode fieldCode;
                    @Override public void validate() {
                        tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
                        memberId = new MemberId(caller.actorId()); draftId = draftId(rawDraftId); fileId = fileId(rawFileId);
                        try { fieldCode = new FieldCode(rawFieldCode); }
                        catch (RuntimeException exception) { throw invalid("file field code is invalid"); }
                        if (version == null || version < 0 || source == null) throw invalid("file field upload is invalid");
                    }
                    @Override public Subject execute() {
                        FileFieldDraft routed = draftRepository.findDraft(tenantId, draftId)
                                .orElseThrow(support::notFound);
                        verifyPreCreateDraftAuthorization(caller, tenantId, memberId, routed);
                        verifyDirectTarget(caller, tenantId, routed, AttachmentTargetOperation.UPLOAD);
                        FileFieldDraft draft = draftRepository.findDraftForUpdate(tenantId, draftId)
                                .orElseThrow(() -> invalid("file field draft is unavailable"));
                        if (!draft.isActiveFor(memberId, draft.getObjectType(), local(clock.instant()))) {
                            throw invalid("file field draft is unavailable");
                        }
                        if (!java.util.Objects.equals(routed.getDirectTarget(), draft.getDirectTarget())) {
                            throw support.notFound();
                        }
                        var staged = draftRepository.findStagedFilesForUpdate(tenantId, draftId).stream()
                                .filter(value -> value.file().getId().sameValueAs(fileId)
                                        && value.field().getCode().sameValueAs(fieldCode)).findFirst()
                                .orElseThrow(() -> invalid("file field candidate is unavailable"));
                        var access = fieldAccessResolver.resolve(tenantId, memberId, draft.getObjectType()).get(fieldCode);
                        if (access == null || !access.accessLevel().covers(FieldAccessLevel.WRITE)
                                || !staged.file().isUploadedBy(memberId) || source.declaredLength() != staged.file().getExpectedBytes()) {
                            throw support.notFound();
                        }
                        AttachmentWorkStore.ContentPlan plan = workStore.findContentPlan(tenantId, fileId)
                                .orElseThrow(support::notFound);
                        var ready = readinessResolver.resolve(tenantId, AttachmentOperation.STORE_SINGLE_STREAM);
                        if (ready == null || !ready.isReady(AttachmentOperation.STORE_SINGLE_STREAM, clock.instant())
                                || !Arrays.equals(ready.binding().copyValue(), plan.binding().copyValue())) throw support.unavailable();
                        AttachmentFile file = staged.file();
                        if (file.getByteState() == AttachmentByteState.STORED) {
                            if (!version.equals(plan.intentVersion()) || plan.evidence() == null || plan.storedIdentity() == null) throw support.conflict();
                        } else if (!version.equals(file.getVersion()) || file.getByteState() != AttachmentByteState.UPLOADING
                                || file.getPublicationState() != AttachmentPublicationState.PRIVATE || !clock.instant().isBefore(plan.deadline())) {
                            throw support.notFound();
                        }
                        return new Subject(tenantId, memberId, draftId, fileId, fieldCode,
                                draft.getDirectTarget(), file, plan, ready);
                    }
                });
        if (!admitted.isSuccess()) return copyFailure(admitted);
        Subject subject = admitted.getData();
        AttachmentStorage.StoreReceipt receipt;
        try {
            receipt = storage.store(new AttachmentStorage.StoreCommand(subject.plan.binding(), subject.plan.locator(),
                    subject.plan.storageGeneration(), subject.file.getContentVersion(), subject.plan.attempt(),
                    subject.file.getExpectedBytes(), subject.file.getExpectedSha256(), deadline(subject.plan, subject.file),
                    policy.uploadOpenTimeout(), policy.uploadIdleTimeout()), source);
        } catch (AttachmentStorage.StoreConflictException exception) { return conflict(); }
        catch (Exception exception) { return unavailable(); }
        if (receipt == null || receipt.identity() == null || receipt.evidence() == null) return unavailable();
        Result<FileFieldUploadedFileVO> finalized = commandTemplate.execute(
                UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD,
                new ServiceOperation<>() {
                    @Override public void validate() { }
                    @Override public FileFieldUploadedFileVO execute() {
                        verifyDirectTarget(caller, subject.tenantId, subject.directTarget,
                                AttachmentTargetOperation.UPLOAD);
                        FileFieldDraft draft = draftRepository.findDraftForUpdate(subject.tenantId, subject.draftId)
                                .orElseThrow(support::notFound);
                        verifyPreCreateDraftAuthorization(caller, subject.tenantId, subject.memberId, draft);
                        if (!java.util.Objects.equals(subject.directTarget, draft.getDirectTarget())
                                || !draft.isActiveFor(subject.memberId, draft.getObjectType(), local(clock.instant()))) {
                            throw support.notFound();
                        }
                        var staged = draftRepository.findStagedFilesForUpdate(subject.tenantId, subject.draftId).stream()
                                .filter(value -> value.file().getId().sameValueAs(subject.fileId)
                                        && value.field().getCode().sameValueAs(subject.fieldCode))
                                .findFirst().orElseThrow(support::notFound);
                        var access = fieldAccessResolver.resolve(subject.tenantId, subject.memberId,
                                        draft.getObjectType()).get(subject.fieldCode);
                        if (access == null || !access.accessLevel().covers(FieldAccessLevel.WRITE)
                                || !staged.file().isUploadedBy(subject.memberId)) {
                            throw support.notFound();
                        }
                        AttachmentFile file = staged.file();
                        AttachmentWorkStore.ContentPlan current = workStore.findContentPlan(subject.tenantId, subject.fileId)
                                .orElseThrow(support::notFound);
                        if (!version.equals(current.intentVersion()) || current.contentVersion() != file.getContentVersion()
                                || !Arrays.equals(current.storageGeneration(), subject.plan.storageGeneration())) throw support.conflict();
                        if (file.getByteState() == AttachmentByteState.STORED) {
                            if (current.evidence() == null || current.storedIdentity() == null
                                    || current.evidence().actualBytes() != receipt.evidence().actualBytes()
                                    || !Arrays.equals(current.evidence().sha256(), receipt.evidence().sha256())
                                    || !Arrays.equals(current.storedIdentity().copyValue(), receipt.identity().copyValue())) {
                                throw support.conflict();
                            }
                            return uploadedFile(file);
                        }
                        if (file.getByteState() != AttachmentByteState.UPLOADING
                                || file.getPublicationState() != AttachmentPublicationState.PRIVATE) throw support.notFound();
                        file.acceptStored(receipt.evidence(), file.getDeclaredMediaType(), local(clock.instant()));
                        fileRepository.save(subject.tenantId, file);
                        workStore.recordStored(subject.tenantId, subject.fileId, file.getContentVersion(), current.attempt(),
                                receipt.identity(), receipt.evidence(), clock.instant());
                        if (!workStore.reserveUsage(subject.tenantId, new AttachmentWorkStore.UsageDelta(
                                0, receipt.evidence().actualBytes(), -file.getExpectedBytes(), -1, -1, 1, 0),
                                policy.usageLimits(), clock.instant())) throw support.unavailable();
                        workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.tenantId, subject.fileId,
                                file.getContentVersion(), AttachmentWorkStore.JobKind.INSPECTION, 1, clock.instant(),
                                clock.instant().plus(policy.inspectionTotalTimeout())));
                        return uploadedFile(file);
                    }
                });
        return finalized;
    }

    @Override
    public Result<FileFieldUploadedFileVO> uploadStatus(AuthenticatedCaller caller, Long rawDraftId,
                                                         String rawFieldCode, Long rawFileId) {
        return commandTemplate.execute(UseCaseOperation.ATTACHMENT_UPLOAD_STATUS_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId; private MemberId memberId; private FileFieldDraftId draftId;
                    private AttachmentFileId fileId; private FieldCode fieldCode;
                    @Override public void validate() {
                        tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
                        memberId = new MemberId(caller.actorId());
                        draftId = draftId(rawDraftId); fileId = fileId(rawFileId);
                        try { fieldCode = new FieldCode(rawFieldCode); }
                        catch (RuntimeException exception) { throw invalid("file field code is invalid"); }
                    }
                    @Override public FileFieldUploadedFileVO execute() {
                        FileFieldDraft routed = draftRepository.findDraft(tenantId, draftId)
                                .orElseThrow(support::notFound);
                        verifyPreCreateDraftAuthorization(caller, tenantId, memberId, routed);
                        verifyDirectTarget(caller, tenantId, routed, AttachmentTargetOperation.STATUS);
                        FileFieldDraft draft = draftRepository.findDraftForUpdate(tenantId, draftId)
                                .orElseThrow(support::notFound);
                        if (!draft.isActiveFor(memberId, draft.getObjectType(), local(clock.instant()))) {
                            throw support.notFound();
                        }
                        if (!java.util.Objects.equals(routed.getDirectTarget(), draft.getDirectTarget())) {
                            throw support.notFound();
                        }
                        AttachmentFile file = draftRepository.findStagedFilesForUpdate(tenantId, draftId).stream()
                                .filter(value -> value.field().getCode().sameValueAs(fieldCode)
                                        && value.file().getId().sameValueAs(fileId))
                                .map(FileFieldRepository.StagedFile::file).findFirst()
                                .orElseThrow(support::notFound);
                        var access = fieldAccessResolver.resolve(tenantId, memberId, draft.getObjectType()).get(fieldCode);
                        if (access == null || !access.accessLevel().covers(FieldAccessLevel.WRITE)
                                || !file.isUploadedBy(memberId)) throw support.notFound();
                        return uploadedFile(file);
                    }
                });
    }

    private Instant deadline(AttachmentWorkStore.ContentPlan plan, AttachmentFile file) {
        Instant total = clock.instant().plus(policy.uploadTotalTimeout());
        return file.getByteState() == AttachmentByteState.STORED ? total
                : plan.deadline().isBefore(total) ? plan.deadline() : total;
    }
    private static FileFieldUploadedFileVO uploadedFile(AttachmentFile file) {
        String status = file.getInspectionState() == AttachmentInspectionState.REJECTED
                || file.getPublicationState() == AttachmentPublicationState.WITHDRAWN
                || file.getPublicationState() == AttachmentPublicationState.DELETED
                || file.getByteState() == AttachmentByteState.REMOVING
                || file.getByteState() == AttachmentByteState.REMOVED ? "FAILED"
                : file.getByteState() == AttachmentByteState.STORED
                && file.getInspectionState() == AttachmentInspectionState.CLEAN ? "UPLOADED"
                : "PENDING";
        String mediaType = file.getMediaType() == null ? file.getDeclaredMediaType() : file.getMediaType();
        long size = file.getActualBytes() > 0 ? file.getActualBytes() : file.getExpectedBytes();
        return new FileFieldUploadedFileVO(file.getId().getValue(), file.getDisplayName(), mediaType, size, status);
    }
    private <T> Result<T> unavailable() { return commandTemplate.execute(UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD,
            new ServiceOperation<>() { @Override public void validate() { throw support.unavailable(); }
                @Override public T execute() { throw new IllegalStateException(); } }); }
    private <T> Result<T> conflict() { return commandTemplate.execute(UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD,
            new ServiceOperation<>() { @Override public void validate() { throw support.conflict(); }
                @Override public T execute() { throw new IllegalStateException(); } }); }
    @SuppressWarnings("unchecked") private static <T> Result<T> copyFailure(Result<?> result) { return (Result<T>) result; }
    private static FileFieldDraftId draftId(Long value) { try { return new FileFieldDraftId(value); }
        catch (RuntimeException exception) { throw invalid("file field draft ID is invalid"); } }
    private static AttachmentFileId fileId(Long value) { try { return new AttachmentFileId(value); }
        catch (RuntimeException exception) { throw invalid("file field file ID is invalid"); } }
    private static LocalDateTime local(Instant time) { return LocalDateTime.ofInstant(time, ZoneOffset.UTC); }
    private void verifyDirectTarget(AuthenticatedCaller caller, TenantId tenantId, FileFieldDraft draft,
                                    AttachmentTargetOperation operation) {
        verifyDirectTarget(caller, tenantId, draft == null ? null : draft.getDirectTarget(), operation);
    }
    private void verifyPreCreateDraftAuthorization(AuthenticatedCaller caller, TenantId tenantId,
                                                   MemberId memberId, FileFieldDraft draft) {
        if (draft != null && draft.getDirectTarget() == null) {
            preCreateDraftAuthorizer.requireCurrentWrite(caller, tenantId, memberId, draft.getObjectType());
        }
    }
    private void verifyDirectTarget(AuthenticatedCaller caller, TenantId tenantId,
                                    io.github.archetom.crm.domain.model.AttachmentTarget target,
                                    AttachmentTargetOperation operation) {
        if (target == null) return;
        var lifecycle = targetAccessRegistry.require(target.type())
                .lockForAttachmentMutation(caller, target, operation);
        if (!tenantId.sameValueAs(lifecycle.tenantId()) || !lifecycle.attachmentsPermitted()) {
            throw support.notFound();
        }
    }
    private static NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, message);
    }
    private record Subject(TenantId tenantId, MemberId memberId, FileFieldDraftId draftId,
                           AttachmentFileId fileId, FieldCode fieldCode,
                           io.github.archetom.crm.domain.model.AttachmentTarget directTarget,
                           AttachmentFile file, AttachmentWorkStore.ContentPlan plan,
                           AttachmentStorageReadinessResolver.Snapshot readiness) { }
}
