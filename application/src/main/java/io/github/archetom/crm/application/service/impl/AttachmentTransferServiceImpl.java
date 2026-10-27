package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.port.out.attachment.AttachmentOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRange;
import io.github.archetom.crm.application.port.out.attachment.AttachmentReadHandle;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorageReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OaRequestAccessResolver;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccess;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccessRegistry;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetOperation;
import io.github.archetom.crm.application.service.AttachmentTransferService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.model.AttachmentContentEvidence;
import io.github.archetom.crm.domain.model.AttachmentByteState;
import io.github.archetom.crm.domain.model.AttachmentPublicationState;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.ApprovalInstanceRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.repository.OaRequestRepository;
import io.github.archetom.crm.domain.repository.StageWorkflowRecordRepository;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Splits short authorization/finalization transactions around a single external byte stream. */
@Service
@RequiredArgsConstructor
public class AttachmentTransferServiceImpl implements AttachmentTransferService {
    private final AttachmentFileRepository fileRepository;
    private final FileFieldRepository fileFieldRepository;
    private final OaRequestRepository oaRequestRepository;
    private final ApprovalInstanceRepository approvalInstanceRepository;
    private final StageWorkflowRecordRepository stageWorkflowRecordRepository;
    private final AttachmentWorkStore workStore;
    private final AttachmentStorage storage;
    private final AttachmentStorageReadinessResolver storageReadinessResolver;
    private final AttachmentRuntimePolicy runtimePolicy;
    private final OrganizationAccessGuard organizationAccessGuard;
    private final OaRequestAccessResolver oaRequestAccessResolver;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final BusinessRecordAccessGuard recordAccessGuard;
    private final AttachmentTargetAccessRegistry targetAccessRegistry;
    private final AttachmentApplicationSupport support;
    private final CommandServiceTemplate commandTemplate;
    private final Clock clock;

    @Override
    public Result<String> upload(AuthenticatedCaller caller, String targetType, Long targetId,
                                 Long fileIdValue, Long version, AttachmentByteSource source) {
        Result<UploadSubject> admission = commandTemplate.execute(UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD,
                new ServiceOperation<>() {
                    private AttachmentTarget target;
                    private AttachmentFileId fileId;
                    private TenantId tenantId;
                    private AttachmentStorageReadinessResolver.Snapshot storeReadiness;
                    @Override public void validate() {
                        target = support.target(targetType, targetId);
                        fileId = support.fileId(fileIdValue);
                        if (version == null || version < 0 || source == null) throw support.invalid("attachment upload is invalid");
                        tenantId = organizationAccessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
                    }
                    @Override public UploadSubject execute() {
                        var lifecycle = targetAccess(target).lockForAttachmentMutation(caller, target,
                                AttachmentTargetOperation.UPLOAD);
                        if (!tenantId.sameValueAs(lifecycle.tenantId()) || !lifecycle.attachmentsPermitted()) throw support.notFound();
                        AttachmentFile file = fileRepository.findByTargetForUpdate(tenantId, target, fileId)
                                .orElseThrow(support::notFound);
                        if (!file.isUploadedBy(new MemberId(caller.actorId()))
                                || source.declaredLength() != file.getExpectedBytes()) {
                            throw support.notFound();
                        }
                        AttachmentWorkStore.ContentPlan plan = workStore.findContentPlan(tenantId, fileId)
                                .orElseThrow(support::notFound);
                        // A lost 202 response is retried with the original intent version. It
                        // still has to stream the exact same bytes through the one-shot store
                        // comparison: stored state is not a blanket success oracle.
                        if (file.getByteState() == AttachmentByteState.STORED) {
                            if (!version.equals(plan.intentVersion())
                                    || plan.storedIdentity() == null || plan.evidence() == null) {
                                throw support.conflict();
                            }
                            storeReadiness = requireWriteReady(tenantId, clock.instant());
                            if (!sameBinding(storeReadiness.binding(), plan.binding())) throw support.unavailable();
                            return new UploadSubject(tenantId, target, fileId, file, plan);
                        }
                        storeReadiness = requireWriteReady(tenantId, clock.instant());
                        Instant now = clock.instant();
                        if (!version.equals(file.getVersion()) || file.getByteState() != AttachmentByteState.UPLOADING
                                || file.getPublicationState() != AttachmentPublicationState.PRIVATE
                                || !now.isBefore(plan.deadline())) {
                            throw support.notFound();
                        }
                        if (!sameBinding(storeReadiness.binding(), plan.binding())) {
                            throw support.unavailable();
                        }
                        return new UploadSubject(tenantId, target, fileId, file, plan);
                    }
                });
        if (!admission.isSuccess()) return copyFailure(admission);
        AttachmentStorage.StoreReceipt receipt;
        try {
            UploadSubject subject = admission.getData();
            receipt = storage.store(new AttachmentStorage.StoreCommand(subject.plan.binding(),
                    subject.plan.locator(), subject.plan.storageGeneration(), subject.file.getContentVersion(), subject.plan.attempt(), subject.file.getExpectedBytes(),
                    subject.file.getExpectedSha256(), uploadDeadline(subject.plan, subject.file,
                    clock.instant()), runtimePolicy.uploadOpenTimeout(),
                    runtimePolicy.uploadIdleTimeout()), source);
        } catch (AttachmentStorage.StoreConflictException exception) {
            return conflict(UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD);
        } catch (Exception exception) {
            return unavailable(UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD);
        }
        if (receipt == null || receipt.identity() == null || receipt.evidence() == null) {
            return unavailable(UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD);
        }

        UploadSubject subject = admission.getData();
        Result<FinalizeResult> finalized = commandTemplate.execute(UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD, new ServiceOperation<>() {
            @Override public void validate() { }
            @Override public FinalizeResult execute() {
                AttachmentFile file = fileRepository.findByTargetForUpdate(subject.tenantId, subject.target,
                        subject.fileId).orElseThrow(support::notFound);
                AttachmentWorkStore.ContentPlan currentPlan = workStore.findContentPlan(subject.tenantId, subject.fileId)
                        .orElseThrow(support::notFound);
                if (!version.equals(currentPlan.intentVersion())
                        || currentPlan.contentVersion() != file.getContentVersion()
                        || !Arrays.equals(currentPlan.storageGeneration(), subject.plan.storageGeneration())) {
                    throw support.conflict();
                }
                // Decide convergence from the state protected by this final transaction, never
                // from the I/O admission snapshot. Concurrent identical PUTs therefore converge.
                if (file.getByteState() == AttachmentByteState.STORED) {
                    AttachmentContentEvidence current = currentPlan.evidence();
                    if (current == null || current.actualBytes() != receipt.evidence().actualBytes()
                            || !Arrays.equals(current.sha256(), receipt.evidence().sha256())
                            || currentPlan.storedIdentity() == null
                            || !Arrays.equals(currentPlan.storedIdentity().copyValue(), receipt.identity().copyValue())) {
                        throw support.conflict();
                    }
                    return new FinalizeResult("ACCEPTED_FOR_INSPECTION", false);
                }
                if (file.getByteState() != AttachmentByteState.UPLOADING
                        || file.getPublicationState() != AttachmentPublicationState.PRIVATE) {
                    // A withdrawal after external I/O has a real exact identity. Persist that
                    // observation and use DELETE_EXACT -> CONFIRM_ABSENCE, never INCOMPLETE_ABORT.
                    if (currentPlan.storedIdentity() == null) {
                        boolean captured = file.captureStoredForRemoval(receipt.evidence(), file.getDeclaredMediaType(),
                                local(clock.instant()));
                        workStore.recordStored(subject.tenantId, subject.fileId, file.getContentVersion(),
                                currentPlan.attempt(), receipt.identity(), receipt.evidence(), clock.instant());
                        if (captured) {
                            workStore.adjustUsage(subject.tenantId, new AttachmentWorkStore.UsageDelta(
                                    0, receipt.evidence().actualBytes(), -file.getExpectedBytes(), -1, -1, 0, 0),
                                    clock.instant());
                            fileRepository.save(subject.tenantId, file);
                        }
                    }
                    workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.tenantId, subject.fileId,
                            subject.file.getContentVersion(), AttachmentWorkStore.JobKind.DELETION, 2,
                            clock.instant(), clock.instant().plus(runtimePolicy.tombstoneRetention())));
                    return new FinalizeResult(null, true);
                }
                file.acceptStored(receipt.evidence(), file.getDeclaredMediaType(), local(clock.instant()));
                fileRepository.save(subject.tenantId, file);
                workStore.recordStored(subject.tenantId, subject.fileId, file.getContentVersion(),
                        subject.plan.attempt(), receipt.identity(), receipt.evidence(), clock.instant());
                if (!workStore.reserveUsage(subject.tenantId, new AttachmentWorkStore.UsageDelta(
                        0, receipt.evidence().actualBytes(), -file.getExpectedBytes(), -1,
                        -1, 1, 0), runtimePolicy.usageLimits(), clock.instant())) {
                    throw support.unavailable();
                }
                workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.tenantId, subject.fileId,
                        file.getContentVersion(), AttachmentWorkStore.JobKind.INSPECTION, 1,
                        clock.instant(), clock.instant().plus(runtimePolicy.inspectionTotalTimeout())));
                return new FinalizeResult("ACCEPTED_FOR_INSPECTION", false);
            }
        });
        if (!finalized.isSuccess()) return copyFailure(finalized);
        return finalized.getData().cleanupRequired()
                ? notFound(UseCaseOperation.ATTACHMENT_CONTENT_UPLOAD)
                : success(finalized.getData().status());
    }

    @Override
    public Result<Download> openDownload(AuthenticatedCaller caller, String targetType, Long targetId,
                                         Long fileIdValue, String rangeHeader) {
        Result<DownloadSubject> admitted = commandTemplate.execute(UseCaseOperation.ATTACHMENT_CONTENT_DOWNLOAD,
                new ServiceOperation<>() {
                    private AttachmentTarget target;
                    private AttachmentFileId fileId;
                    private TenantId tenantId;
                    private AttachmentRange range;
                    private AttachmentStorageReadinessResolver.Snapshot readiness;
                    @Override public void validate() {
                        target = support.target(targetType, targetId);
                        fileId = support.fileId(fileIdValue);
                        tenantId = organizationAccessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_READ);
                    }
                    @Override public DownloadSubject execute() {
                        var lifecycle = targetAccess(target).lockForAttachmentMutation(caller, target,
                                AttachmentTargetOperation.DOWNLOAD);
                        if (!tenantId.sameValueAs(lifecycle.tenantId()) || !lifecycle.attachmentsPermitted()) {
                            throw support.notFound();
                        }
                        AttachmentFile file = fileRepository.findByTargetForUpdate(tenantId, target, fileId)
                                .filter(AttachmentFile::isAvailable).orElseThrow(support::notFound);
                        range = support.singleRange(rangeHeader, file.getActualBytes());
                        AttachmentOperation operation = range == null ? AttachmentOperation.READ_FULL
                                : AttachmentOperation.READ_SINGLE_RANGE;
                        readiness = storageReadinessResolver.resolve(tenantId, operation);
                        if (readiness == null || !readiness.isReady(operation, clock.instant())) throw support.unavailable();
                        AttachmentWorkStore.ContentPlan plan = workStore.findContentPlan(tenantId, fileId)
                                .filter(value -> value.storedIdentity() != null && value.evidence() != null)
                                .orElseThrow(support::notFound);
                        if (!sameBinding(readiness.binding(), plan.binding())) throw support.unavailable();
                        if (range == null) range = new AttachmentRange(0, file.getActualBytes(), file.getActualBytes());
                        AttachmentStorageReadinessResolver.Snapshot rangeReadiness = storageReadinessResolver.resolve(
                                tenantId, AttachmentOperation.READ_SINGLE_RANGE);
                        boolean rangeReady = rangeReadiness != null && rangeReadiness.isReady(
                                AttachmentOperation.READ_SINGLE_RANGE, clock.instant());
                        if (!workStore.reserveUsage(tenantId, new AttachmentWorkStore.UsageDelta(
                                0, 0, 0, 0, 0, 0, 1), runtimePolicy.usageLimits(), clock.instant())) {
                            throw support.unavailable();
                        }
                        AttachmentWorkStore.ReadLease lease = workStore.acquireReadLease(tenantId, fileId,
                                file.getContentVersion(), clock.instant().plus(runtimePolicy.downloadTotalTimeout()),
                                clock.instant());
                        return new DownloadSubject(file, plan, range, rangeReady, lease);
                    }
        });
        return completeDownload(admitted, rangeHeader);
    }

    @Override
    public Result<Download> openOaFileFieldDownload(AuthenticatedCaller caller, Long rawRequestId,
                                                     String rawFieldCode, Long fileIdValue,
                                                     String rangeHeader) {
        Result<DownloadSubject> admitted = commandTemplate.execute(UseCaseOperation.ATTACHMENT_CONTENT_DOWNLOAD,
                new ServiceOperation<>() {
                    private AttachmentFileId fileId;
                    private OaRequestId requestId;
                    private FieldCode fieldCode;
                    private TenantId tenantId;
                    private OaRequestAccessResolver.OaRequestAccess requestAccess;
                    private AttachmentRange range;
                    private AttachmentStorageReadinessResolver.Snapshot readiness;

                    @Override public void validate() {
                        fileId = support.fileId(fileIdValue);
                        try {
                            requestId = new OaRequestId(rawRequestId);
                            fieldCode = new FieldCode(rawFieldCode);
                        } catch (RuntimeException exception) {
                            throw support.notFound();
                        }
                        tenantId = organizationAccessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_READ);
                        requestAccess = oaRequestAccessResolver.access(caller);
                        if (!tenantId.sameValueAs(requestAccess.tenantId())) throw support.notFound();
                    }

                    @Override public DownloadSubject execute() {
                        oaRequestRepository.findVisibleById(tenantId, requestId, requestAccess.memberId(),
                                requestAccess.tenantManager()).orElseThrow(support::notFound);
                        EffectiveFieldAccess fieldAccess = fieldAccessResolver.resolve(tenantId,
                                        requestAccess.memberId(), BusinessObjectType.OA_REQUEST)
                                .get(fieldCode);
                        if (fieldAccess == null || !fieldAccess.accessLevel().covers(FieldAccessLevel.READ)
                                || fieldAccess.maskType() != FieldMaskType.NONE) throw support.notFound();
                        FileFieldRepository.BoundFile bound = fileFieldRepository.findOaValues(tenantId, requestId)
                                .getOrDefault(fieldCode, java.util.List.of()).stream()
                                .filter(value -> value.file().getId().sameValueAs(fileId))
                                .findFirst().orElseThrow(support::notFound);
                        AttachmentFile file = fileRepository.findByIdForUpdate(tenantId, fileId)
                                .filter(AttachmentFile::isAvailable).orElseThrow(support::notFound);
                        if (file.getContentVersion() != bound.contentVersion()) throw support.notFound();
                        range = support.singleRange(rangeHeader, file.getActualBytes());
                        AttachmentOperation operation = range == null ? AttachmentOperation.READ_FULL
                                : AttachmentOperation.READ_SINGLE_RANGE;
                        readiness = storageReadinessResolver.resolve(tenantId, operation);
                        if (readiness == null || !readiness.isReady(operation, clock.instant())) throw support.unavailable();
                        AttachmentWorkStore.ContentPlan plan = workStore.findContentPlan(tenantId, fileId)
                                .filter(value -> value.contentVersion() == bound.contentVersion()
                                        && value.storedIdentity() != null && value.evidence() != null
                                        && Arrays.equals(value.evidence().sha256(), bound.evidenceDigest()))
                                .orElseThrow(support::notFound);
                        if (!sameBinding(readiness.binding(), plan.binding())) throw support.unavailable();
                        if (range == null) range = new AttachmentRange(0, file.getActualBytes(), file.getActualBytes());
                        AttachmentStorageReadinessResolver.Snapshot rangeReadiness = storageReadinessResolver.resolve(
                                tenantId, AttachmentOperation.READ_SINGLE_RANGE);
                        boolean rangeReady = rangeReadiness != null && rangeReadiness.isReady(
                                AttachmentOperation.READ_SINGLE_RANGE, clock.instant());
                        if (!workStore.reserveUsage(tenantId, new AttachmentWorkStore.UsageDelta(
                                0, 0, 0, 0, 0, 0, 1), runtimePolicy.usageLimits(), clock.instant())) {
                            throw support.unavailable();
                        }
                        AttachmentWorkStore.ReadLease lease = workStore.acquireReadLease(tenantId, fileId,
                                file.getContentVersion(), clock.instant().plus(runtimePolicy.downloadTotalTimeout()),
                                clock.instant());
                        return new DownloadSubject(file, plan, range, rangeReady, lease);
                    }
                });
        return completeDownload(admitted, rangeHeader);
    }

    @Override
    public Result<Download> openCrmFileFieldDownload(AuthenticatedCaller caller, String targetType,
                                                      Long targetIdValue, String rawFieldCode,
                                                      Long fileIdValue, String rangeHeader) {
        Result<DownloadSubject> admitted = commandTemplate.execute(UseCaseOperation.ATTACHMENT_CONTENT_DOWNLOAD,
                new ServiceOperation<>() {
                    private AttachmentTarget target;
                    private AttachmentFileId fileId;
                    private FieldCode fieldCode;
                    private TenantId tenantId;
                    private AttachmentRange range;
                    private AttachmentStorageReadinessResolver.Snapshot readiness;

                    @Override public void validate() {
                        target = support.target(targetType, targetIdValue);
                        fileId = support.fileId(fileIdValue);
                        try { fieldCode = new FieldCode(rawFieldCode); }
                        catch (RuntimeException exception) { throw support.notFound(); }
                        tenantId = organizationAccessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_READ);
                    }

                    @Override public DownloadSubject execute() {
                        var lifecycle = targetAccess(target).lockForAttachmentMutation(caller, target,
                                AttachmentTargetOperation.DOWNLOAD);
                        if (!tenantId.sameValueAs(lifecycle.tenantId()) || !lifecycle.attachmentsPermitted()) {
                            throw support.notFound();
                        }
                        BusinessObjectType objectType;
                        try { objectType = BusinessObjectType.valueOf(target.type().name()); }
                        catch (RuntimeException exception) { throw support.notFound(); }
                        EffectiveFieldAccess fieldAccess = fieldAccessResolver.resolve(tenantId,
                                        new MemberId(caller.actorId()), objectType).get(fieldCode);
                        if (fieldAccess == null || !fieldAccess.accessLevel().covers(FieldAccessLevel.READ)
                                || fieldAccess.maskType() != FieldMaskType.NONE) throw support.notFound();
                        FileFieldRepository.BoundFile bound = fileFieldRepository.findCrmValues(tenantId, target)
                                .getOrDefault(fieldCode, java.util.List.of()).stream()
                                .filter(value -> value.file().getId().sameValueAs(fileId))
                                .findFirst().orElseThrow(support::notFound);
                        AttachmentFile file = fileRepository.findByIdForUpdate(tenantId, fileId)
                                .filter(AttachmentFile::isAvailable).orElseThrow(support::notFound);
                        if (file.getContentVersion() != bound.contentVersion()) throw support.notFound();
                        range = support.singleRange(rangeHeader, file.getActualBytes());
                        AttachmentOperation operation = range == null ? AttachmentOperation.READ_FULL
                                : AttachmentOperation.READ_SINGLE_RANGE;
                        readiness = storageReadinessResolver.resolve(tenantId, operation);
                        if (readiness == null || !readiness.isReady(operation, clock.instant())) {
                            throw support.unavailable();
                        }
                        AttachmentWorkStore.ContentPlan plan = workStore.findContentPlan(tenantId, fileId)
                                .filter(value -> value.contentVersion() == bound.contentVersion()
                                        && value.storedIdentity() != null && value.evidence() != null
                                        && Arrays.equals(value.evidence().sha256(), bound.evidenceDigest()))
                                .orElseThrow(support::notFound);
                        if (!sameBinding(readiness.binding(), plan.binding())) throw support.unavailable();
                        if (range == null) range = new AttachmentRange(0, file.getActualBytes(), file.getActualBytes());
                        AttachmentStorageReadinessResolver.Snapshot rangeReadiness = storageReadinessResolver.resolve(
                                tenantId, AttachmentOperation.READ_SINGLE_RANGE);
                        boolean rangeReady = rangeReadiness != null && rangeReadiness.isReady(
                                AttachmentOperation.READ_SINGLE_RANGE, clock.instant());
                        if (!workStore.reserveUsage(tenantId, new AttachmentWorkStore.UsageDelta(
                                0, 0, 0, 0, 0, 0, 1), runtimePolicy.usageLimits(), clock.instant())) {
                            throw support.unavailable();
                        }
                        AttachmentWorkStore.ReadLease lease = workStore.acquireReadLease(tenantId, fileId,
                                file.getContentVersion(), clock.instant().plus(runtimePolicy.downloadTotalTimeout()),
                                clock.instant());
                        return new DownloadSubject(file, plan, range, rangeReady, lease);
                    }
                });
        return completeDownload(admitted, rangeHeader);
    }

    @Override
    public Result<Download> openApprovalFileFieldDownload(AuthenticatedCaller caller, Long rawInstanceId,
                                                            String rawFieldCode, Long fileIdValue,
                                                            String rangeHeader) {
        Result<DownloadSubject> admitted = commandTemplate.execute(UseCaseOperation.ATTACHMENT_CONTENT_DOWNLOAD,
                new ServiceOperation<>() {
                    private TenantId tenantId; private MemberId memberId; private ApprovalInstanceId instanceId;
                    private AttachmentFileId fileId; private FieldCode fieldCode;
                    @Override public void validate() {
                        tenantId = organizationAccessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_READ);
                        organizationAccessGuard.requireActiveMembership(caller, AuthorityCatalog.APPROVALS_READ);
                        memberId = new MemberId(caller.actorId()); fileId = support.fileId(fileIdValue);
                        try { instanceId = new ApprovalInstanceId(rawInstanceId); fieldCode = new FieldCode(rawFieldCode); }
                        catch (RuntimeException exception) { throw support.notFound(); }
                    }
                    @Override public DownloadSubject execute() {
                        var instance = approvalInstanceRepository.findById(tenantId, instanceId)
                                .orElseThrow(support::notFound);
                        if (!instance.isParticipant(memberId)
                                && !caller.hasAuthority(AuthorityCatalog.APPROVALS_MANAGE)
                                && !instance.isManager(memberId)) throw support.notFound();
                        TenantId recordTenant = recordAccessGuard.requireVisible(caller,
                                instance.getObjectType(), instance.getSubjectId(), false);
                        if (!tenantId.sameValueAs(recordTenant)) throw support.notFound();
                        EffectiveFieldAccess access = fieldAccessResolver.resolve(tenantId, memberId,
                                instance.getObjectType()).get(fieldCode);
                        if (access == null || !access.accessLevel().covers(FieldAccessLevel.READ)
                                || access.maskType() != FieldMaskType.NONE) throw support.notFound();
                        FileFieldRepository.BoundFile bound = fileFieldRepository
                                .findApprovalInstanceValues(tenantId, instanceId)
                                .getOrDefault(fieldCode, java.util.List.of()).stream()
                                .filter(value -> value.file().getId().sameValueAs(fileId)).findFirst()
                                .orElseThrow(support::notFound);
                        return snapshotDownload(tenantId, fileId, bound, rangeHeader);
                    }
                });
        return completeDownload(admitted, rangeHeader);
    }

    @Override
    public Result<Download> openStageFileFieldDownload(AuthenticatedCaller caller, Long rawRecordId,
                                                         String rawStageCode, String rawFieldCode,
                                                         Long fileIdValue, String rangeHeader) {
        Result<DownloadSubject> admitted = commandTemplate.execute(UseCaseOperation.ATTACHMENT_CONTENT_DOWNLOAD,
                new ServiceOperation<>() {
                    private TenantId tenantId; private MemberId memberId; private StageWorkflowRecordId recordId;
                    private AttachmentFileId fileId; private FieldCode fieldCode; private String stageCode;
                    @Override public void validate() {
                        tenantId = organizationAccessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_READ);
                        organizationAccessGuard.requireActiveMembership(caller, AuthorityCatalog.WORKFLOWS_READ);
                        memberId = new MemberId(caller.actorId()); fileId = support.fileId(fileIdValue);
                        try { recordId = new StageWorkflowRecordId(rawRecordId); fieldCode = new FieldCode(rawFieldCode); }
                        catch (RuntimeException exception) { throw support.notFound(); }
                        stageCode = rawStageCode == null ? null : rawStageCode.trim();
                        if (stageCode == null || stageCode.isEmpty() || stageCode.length() > 64) throw support.notFound();
                    }
                    @Override public DownloadSubject execute() {
                        var record = stageWorkflowRecordRepository.findById(tenantId, recordId)
                                .orElseThrow(support::notFound);
                        TenantId recordTenant = recordAccessGuard.requireVisible(caller,
                                record.getObjectType(), record.getSubjectId(), false);
                        if (!tenantId.sameValueAs(recordTenant)) throw support.notFound();
                        EffectiveFieldAccess access = fieldAccessResolver.resolve(tenantId, memberId,
                                record.getObjectType()).get(fieldCode);
                        if (access == null || !access.accessLevel().covers(FieldAccessLevel.READ)
                                || access.maskType() != FieldMaskType.NONE) throw support.notFound();
                        FileFieldRepository.BoundFile bound = fileFieldRepository
                                .findStageWorkflowValues(tenantId, recordId)
                                .getOrDefault(stageCode, java.util.Map.of())
                                .getOrDefault(fieldCode, java.util.List.of()).stream()
                                .filter(value -> value.file().getId().sameValueAs(fileId)).findFirst()
                                .orElseThrow(support::notFound);
                        return snapshotDownload(tenantId, fileId, bound, rangeHeader);
                    }
                });
        return completeDownload(admitted, rangeHeader);
    }

    private DownloadSubject snapshotDownload(TenantId tenantId, AttachmentFileId fileId,
                                             FileFieldRepository.BoundFile bound,
                                             String rangeHeader) {
        AttachmentFile file = fileRepository.findByIdForUpdate(tenantId, fileId)
                .filter(AttachmentFile::isAvailable).orElseThrow(support::notFound);
        if (file.getContentVersion() != bound.contentVersion()) throw support.notFound();
        AttachmentRange range = support.singleRange(rangeHeader, file.getActualBytes());
        AttachmentOperation operation = range == null ? AttachmentOperation.READ_FULL
                : AttachmentOperation.READ_SINGLE_RANGE;
        AttachmentStorageReadinessResolver.Snapshot readiness = storageReadinessResolver.resolve(tenantId, operation);
        if (readiness == null || !readiness.isReady(operation, clock.instant())) throw support.unavailable();
        AttachmentWorkStore.ContentPlan plan = workStore.findContentPlan(tenantId, fileId)
                .filter(value -> value.contentVersion() == bound.contentVersion()
                        && value.storedIdentity() != null && value.evidence() != null
                        && Arrays.equals(value.evidence().sha256(), bound.evidenceDigest()))
                .orElseThrow(support::notFound);
        if (!sameBinding(readiness.binding(), plan.binding())) throw support.unavailable();
        if (range == null) range = new AttachmentRange(0, file.getActualBytes(), file.getActualBytes());
        AttachmentStorageReadinessResolver.Snapshot rangeReadiness = storageReadinessResolver.resolve(
                tenantId, AttachmentOperation.READ_SINGLE_RANGE);
        boolean rangeReady = rangeReadiness != null && rangeReadiness.isReady(
                AttachmentOperation.READ_SINGLE_RANGE, clock.instant());
        if (!workStore.reserveUsage(tenantId, new AttachmentWorkStore.UsageDelta(
                0, 0, 0, 0, 0, 0, 1), runtimePolicy.usageLimits(), clock.instant())) {
            throw support.unavailable();
        }
        AttachmentWorkStore.ReadLease lease = workStore.acquireReadLease(tenantId, fileId,
                file.getContentVersion(), clock.instant().plus(runtimePolicy.downloadTotalTimeout()),
                clock.instant());
        return new DownloadSubject(file, plan, range, rangeReady, lease);
    }

    private Result<Download> completeDownload(Result<DownloadSubject> admitted, String rangeHeader) {
        if (!admitted.isSuccess()) return copyFailure(admitted);
        DownloadSubject subject = admitted.getData();
        try {
            AttachmentReadHandle handle = storage.open(new AttachmentStorage.OpenCommand(
                    subject.plan.binding(), subject.plan.storedIdentity(), subject.plan.evidence(),
                    subject.range, subject.lease.expiresAt(), runtimePolicy.downloadOpenTimeout(),
                    runtimePolicy.downloadIdleTimeout()));
            if (handle == null) {
                workStore.releaseReadLease(subject.lease, clock.instant());
                return unavailable(UseCaseOperation.ATTACHMENT_CONTENT_DOWNLOAD);
            }
            return success(new Download(new LeasedReadHandle(handle, workStore, subject.lease, clock),
                    subject.file.getDisplayName(), subject.file.getMediaType(),
                    subject.range.total(), subject.range.offset(), subject.range.length(),
                    rangeHeader != null && !rangeHeader.isBlank(), subject.singleRangeReady));
        } catch (Exception exception) {
            workStore.releaseReadLease(subject.lease, clock.instant());
            return unavailable(UseCaseOperation.ATTACHMENT_CONTENT_DOWNLOAD);
        }
    }

    private AttachmentStorageReadinessResolver.Snapshot requireWriteReady(TenantId tenantId, Instant now) {
        if (!runtimePolicy.enabled()) throw support.unavailable();
        AttachmentStorageReadinessResolver.Snapshot readiness = storageReadinessResolver.resolve(tenantId,
                AttachmentOperation.STORE_SINGLE_STREAM);
        if (readiness == null || !readiness.isReady(AttachmentOperation.STORE_SINGLE_STREAM, now)) {
            throw support.unavailable();
        }
        return readiness;
    }
    private AttachmentTargetAccess targetAccess(AttachmentTarget target) {
        try { return targetAccessRegistry.require(target.type()); }
        catch (UnsupportedOperationException exception) { throw support.invalid("attachment target is unsupported"); }
    }
    private static LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC).withNano(instant.getNano() / 1_000 * 1_000);
    }
    private Instant uploadDeadline(AttachmentWorkStore.ContentPlan plan, AttachmentFile file, Instant now) {
        Instant total = now.plus(runtimePolicy.uploadTotalTimeout());
        // Exact STORED replay is a response-loss recovery, not a new upload attempt.  The
        // original intent TTL limits only the first write; the replay is bounded by its own I/O
        // deadline and still proves version plus content evidence.
        return file.getByteState() == AttachmentByteState.STORED ? total
                : (plan.deadline().isBefore(total) ? plan.deadline() : total);
    }
    private <T> Result<T> unavailable(UseCaseOperation operation) {
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            @Override public void validate() { throw support.unavailable(); }
            @Override public T execute() { throw new IllegalStateException(); }
        });
    }
    private <T> Result<T> conflict(UseCaseOperation operation) {
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            @Override public void validate() { throw support.conflict(); }
            @Override public T execute() { throw new IllegalStateException(); }
        });
    }
    private <T> Result<T> notFound(UseCaseOperation operation) {
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            @Override public void validate() { throw support.notFound(); }
            @Override public T execute() { throw new IllegalStateException(); }
        });
    }
    @SuppressWarnings("unchecked")
    private static <T> Result<T> copyFailure(Result<?> value) { return (Result<T>) value; }
    private static <T> Result<T> success(T value) {
        Result<T> result = new Result<>(); result.setSuccess(true); result.setData(value); return result;
    }
    private static boolean sameBinding(io.github.archetom.crm.application.port.out.attachment.StorageBindingRef left,
                                       io.github.archetom.crm.application.port.out.attachment.StorageBindingRef right) {
        return left != null && right != null && Arrays.equals(left.copyValue(), right.copyValue());
    }
    private record UploadSubject(TenantId tenantId, AttachmentTarget target, AttachmentFileId fileId,
                                 AttachmentFile file, AttachmentWorkStore.ContentPlan plan) { }
    private record DownloadSubject(AttachmentFile file, AttachmentWorkStore.ContentPlan plan,
                                   AttachmentRange range, boolean singleRangeReady,
                                   AttachmentWorkStore.ReadLease lease) { }
    private record FinalizeResult(String status, boolean cleanupRequired) { }

    /** A read lease is same-call-stack only: it cannot be used as a browser bearer credential. */
    private static final class LeasedReadHandle implements AttachmentReadHandle {
        private final AttachmentReadHandle delegate;
        private final AttachmentWorkStore workStore;
        private final AttachmentWorkStore.ReadLease lease;
        private final Clock clock;
        private boolean released;

        private LeasedReadHandle(AttachmentReadHandle delegate, AttachmentWorkStore workStore,
                                 AttachmentWorkStore.ReadLease lease, Clock clock) {
            this.delegate = delegate; this.workStore = workStore; this.lease = lease; this.clock = clock;
        }
        @Override public InputStream inputStream() throws IOException {
            return new FilterInputStream(delegate.inputStream()) {
                private void requireBeforeExpiry() throws IOException {
                    if (!clock.instant().isBefore(lease.expiresAt())) throw new IOException("attachment read lease expired");
                }
                @Override public int read() throws IOException { requireBeforeExpiry(); return super.read(); }
                @Override public int read(byte[] value, int offset, int length) throws IOException {
                    requireBeforeExpiry(); return super.read(value, offset, length);
                }
            };
        }
        @Override public void cancel() { try { delegate.cancel(); } finally { release(); } }
        @Override public void close() throws IOException { try { delegate.close(); } finally { release(); } }
        private synchronized void release() {
            if (released) return;
            RuntimeException failure = null;
            // A close races reservation/expiry transactions in a different HTTP thread. The
            // durable release is idempotent, so retry transaction-abort/deadlock failures before
            // allowing the stream teardown to surface the last unexpected error.
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    workStore.releaseReadLease(lease, clock.instant());
                    released = true;
                    return;
                } catch (RuntimeException exception) {
                    failure = exception;
                    Thread.onSpinWait();
                }
            }
            throw failure;
        }
    }
}
