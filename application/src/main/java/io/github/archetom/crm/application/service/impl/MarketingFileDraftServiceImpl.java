package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.port.out.attachment.AttachmentInspectionReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentReadiness;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorageReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.security.MarketingFileDraftCredential;
import io.github.archetom.crm.application.security.MarketingFormPublicId;
import io.github.archetom.crm.application.service.MarketingFileDraftService;
import io.github.archetom.crm.application.service.PublicMarketingFileRuntimeStatus;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.AttachmentUploadIntentVO;
import io.github.archetom.crm.application.vo.PublicMarketingFileDraftVO;
import io.github.archetom.crm.application.vo.PublicMarketingFileDraftWithdrawalVO;
import io.github.archetom.crm.application.vo.PublicMarketingFileUploadStatusVO;
import io.github.archetom.crm.application.properties.PublicMarketingFileProperties;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.MarketingFileDraft;
import io.github.archetom.crm.domain.entity.MarketingForm;
import io.github.archetom.crm.domain.model.AttachmentByteState;
import io.github.archetom.crm.domain.model.AttachmentInspectionState;
import io.github.archetom.crm.domain.model.AttachmentPublicationState;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FileFieldValue;
import io.github.archetom.crm.domain.model.MarketingFormTargetType;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.repository.MarketingFileDraftRepository;
import io.github.archetom.crm.domain.repository.MarketingFormRepository;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MarketingFileDraftId;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Anonymous B29b FILE candidate pipeline. It deliberately has no caller or member argument. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingFileDraftServiceImpl implements MarketingFileDraftService {
    private final MarketingFormRepository formRepository;
    private final MarketingFileDraftRepository draftRepository;
    private final FieldDefinitionRepository fieldRepository;
    private final AttachmentFileRepository fileRepository;
    private final FileFieldRepository fileFieldRepository;
    private final MarketingFormPolicy formPolicy;
    private final CustomizationObjectLock objectLock;
    private final AttachmentWorkStore workStore;
    private final AttachmentStorage storage;
    private final AttachmentStorageReadinessResolver storageReadinessResolver;
    private final AttachmentInspectionReadinessResolver inspectionReadinessResolver;
    private final AttachmentRuntimePolicy runtimePolicy;
    private final PublicMarketingFileProperties publicProperties;
    private final PublicMarketingFileRuntimeStatus publicRuntimeStatus;
    private final AttachmentApplicationSupport attachmentSupport;
    private final CommandServiceTemplate commandTemplate;
    private final Clock clock;

    @Override
    public Result<PublicMarketingFileDraftVO> create(String publicId) {
        return commandTemplate.execute(UseCaseOperation.MARKETING_FILE_DRAFT_CREATE,
                new ServiceOperation<>() {
                    private MarketingFormPublicId.Parts parts;
                    @Override public void validate() {
                        parts = parts(publicId);
                        requireAdmissionEnabled();
                        requireNewDraftReadiness(parts.tenantId());
                    }
                    @Override public PublicMarketingFileDraftVO execute() {
                        MarketingForm form = activeFormForUpdate(parts);
                        objectLock.acquire(parts.tenantId(), BusinessObjectType.LEAD);
                        var validated = formPolicy.validate(parts.tenantId(), form.getOwnerMemberId(),
                                form.getFields(), true);
                        requireConfiguredFiles(form, validated.definitions());
                        draftRepository.lockFormCapacity(parts.tenantId(), form.getId());
                        if (draftRepository.countActiveForFormForUpdate(parts.tenantId(), form.getId())
                                >= publicProperties.getMaxActiveDraftsPerForm()) {
                            throw attachmentSupport.unavailable();
                        }
                        MarketingFileDraftCredential.Issued credential = MarketingFileDraftCredential.issue();
                        Instant now = clock.instant();
                        MarketingFileDraft draft = MarketingFileDraft.create(parts.tenantId(), form.getId(),
                                form.getVersion(), credential.digest(), local(now.plus(publicProperties.getDraftTtl())),
                                local(now));
                        draftRepository.save(parts.tenantId(), draft);
                        return new PublicMarketingFileDraftVO(draft.getId().getValue(),
                                publicFileCodes(form, validated.definitions()), credential.secret(),
                                draft.getDeadline());
                    }
                });
    }

    @Override
    public Result<AttachmentUploadIntentVO> createIntent(
            String publicId, Long rawDraftId, String authorization, String rawFieldCode,
            AttachmentUploadIntentCreateRequest request) {
        Result<IntentAdmission> admitted = commandTemplate.execute(
                UseCaseOperation.MARKETING_FILE_UPLOAD_INTENT_CREATE, new ServiceOperation<>() {
                    private MarketingFormPublicId.Parts parts;
                    @Override public void validate() {
                        parts = parts(publicId); requireAdmissionEnabled();
                        if (request == null || request.getExpectedBytes() == null) throw invalid("marketing file upload intent is invalid");
                        if (request.getExpectedBytes() > runtimePolicy.maxFileBytes()) throw attachmentSupport.unavailable();
                    }
                    @Override public IntentAdmission execute() {
                        MarketingForm form = activeFormForUpdate(parts);
                        objectLock.acquire(parts.tenantId(), BusinessObjectType.LEAD);
                        var validated = formPolicy.validate(parts.tenantId(), form.getOwnerMemberId(), form.getFields(), true);
                        MarketingFileDraft draft = lockedDraft(parts, form, rawDraftId, authorization, true);
                        FieldDefinition field = publicFileField(parts.tenantId(), form, validated.definitions(), rawFieldCode);
                        LocalDateTime now = local(clock.instant());
                        List<MarketingFileDraftRepository.StagedFile> staged = draftRepository
                                .findStagedFilesForUpdate(parts.tenantId(), draft.getId());
                        if (!draft.isActiveFor(form.getId(), form.getVersion(), now)
                                || staged.size() >= publicProperties.getMaxFilesPerDraft()
                                || draft.getReservedBytes() > publicProperties.getMaxBytesPerDraft() - request.getExpectedBytes()) {
                            throw attachmentSupport.unavailable();
                        }
                        String name = attachmentSupport.displayName(request.getDisplayName());
                        String mediaType = attachmentSupport.declaredMediaType(request.getDeclaredMediaType(), name);
                        var inspection = inspectionReadinessResolver.resolve(parts.tenantId());
                        AttachmentStorageReadinessResolver.Snapshot readiness = storageReadinessResolver.resolve(
                                parts.tenantId(), AttachmentOperation.ALLOCATE_WRITE);
                        if (inspection == null || !inspection.allows(mediaType, clock.instant())
                                || !ready(readiness, AttachmentOperation.ALLOCATE_WRITE)
                                || !ready(readiness, AttachmentOperation.STORE_SINGLE_STREAM)
                                || !ready(readiness, AttachmentOperation.PROBE_EXACT)
                                || !ready(readiness, AttachmentOperation.ABORT_INCOMPLETE)) {
                            publicRuntimeStatus.recordFilesNotReady();
                            throw attachmentSupport.unavailable();
                        }
                        publicRuntimeStatus.recordReadinessReady();
                        return new IntentAdmission(parts, form.getId().getValue(), draft.getId(), field,
                                name, mediaType, attachmentSupport.expectedSha256(request.getExpectedSha256()),
                                request.getExpectedBytes(), readiness, clock.instant(),
                                draft.getDeadline().toInstant(ZoneOffset.UTC));
                    }
                });
        if (!admitted.isSuccess()) return copyFailure(admitted);
        IntentAdmission subject = admitted.getData();
        Instant deadline = noLaterThan(subject.now().plus(runtimePolicy.intentTtl()),
                subject.draftDeadline());
        AttachmentStorage.Allocation allocation;
        try {
            allocation = storage.allocate(subject.readiness().binding(), subject.expectedBytes(), deadline);
        } catch (Exception exception) {
            publicRuntimeStatus.recordRuntimeFailure();
            return unavailable(UseCaseOperation.MARKETING_FILE_UPLOAD_INTENT_CREATE);
        }
        if (allocation == null) {
            publicRuntimeStatus.recordRuntimeFailure();
            return unavailable(UseCaseOperation.MARKETING_FILE_UPLOAD_INTENT_CREATE);
        }
        publicRuntimeStatus.recordAdapterSuccess();
        Result<AttachmentUploadIntentVO> persisted = commandTemplate.execute(
                UseCaseOperation.MARKETING_FILE_UPLOAD_INTENT_CREATE, new ServiceOperation<>() {
                    @Override public void validate() { }
                    @Override public AttachmentUploadIntentVO execute() {
                        MarketingForm form = activeFormForUpdate(subject.parts());
                        objectLock.acquire(subject.parts().tenantId(), BusinessObjectType.LEAD);
                        var validated = formPolicy.validate(subject.parts().tenantId(), form.getOwnerMemberId(), form.getFields(), true);
                        MarketingFileDraft draft = lockedDraft(subject.parts(), form, subject.draftId().getValue(), authorization, true);
                        FieldDefinition field = publicFileField(subject.parts().tenantId(), form,
                                validated.definitions(), subject.field().getCode().getValue());
                        if (!field.getId().sameValueAs(subject.field().getId())) throw unavailableError();
                        List<MarketingFileDraftRepository.StagedFile> staged = draftRepository
                                .findStagedFilesForUpdate(subject.parts().tenantId(), draft.getId());
                        LocalDateTime now = local(clock.instant());
                        draft.reserve(subject.expectedBytes(), staged.size(), publicProperties.getMaxFilesPerDraft(),
                                publicProperties.getMaxBytesPerDraft(), now);
                        AttachmentFile file = AttachmentFile.createPublicMarketingIntent(subject.parts().tenantId(),
                                draft.getId(), subject.displayName(), subject.mediaType(), subject.expectedBytes(),
                                subject.expectedSha(), local(subject.now()));
                        fileRepository.saveStagedIntent(subject.parts().tenantId(), file);
                        if (!workStore.reserveUsage(subject.parts().tenantId(), new AttachmentWorkStore.UsageDelta(
                                0, 0, subject.expectedBytes(), 1, 1, 0, 0), runtimePolicy.usageLimits(), subject.now())) {
                            throw attachmentSupport.unavailable();
                        }
                        draftRepository.stage(subject.parts().tenantId(), draft.getId(), field, file.getId(), staged.size());
                        draftRepository.save(subject.parts().tenantId(), draft);
                        workStore.createContentPlan(subject.parts().tenantId(), file.getId(), file.getContentVersion(),
                                file.getVersion(), subject.readiness().binding(), allocation.locator(),
                                allocation.storageGeneration(), 1, deadline);
                        Instant reconciliationDeadline = deadline.plus(runtimePolicy.tombstoneRetention());
                        Instant firstRetention = noLaterThan(
                                subject.now().plus(publicProperties.getDraftTtl()), subject.draftDeadline());
                        // Retention is deliberately scheduled at the draft deadline. Its own
                        // recovery window must start there too: an upload-intent TTL can be
                        // shorter than the draft TTL, in which case using its deadline would
                        // make this job terminal before it ever becomes due.
                        Instant retentionDeadline = retentionDeadline(firstRetention,
                                runtimePolicy.tombstoneRetention());
                        workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.parts().tenantId(), file.getId(),
                                file.getContentVersion(), AttachmentWorkStore.JobKind.UPLOAD_RECONCILIATION,
                                1, deadline, reconciliationDeadline));
                        workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.parts().tenantId(), file.getId(),
                                file.getContentVersion(), AttachmentWorkStore.JobKind.RETENTION_SWEEP,
                                1, firstRetention, retentionDeadline));
                        return new AttachmentUploadIntentVO(file.getId().getValue(), file.getDisplayName(),
                                file.getDeclaredMediaType(), file.getExpectedBytes(), "UPLOADING", file.getVersion(),
                                local(deadline), draft.getId().getValue());
                    }
                });
        if (!persisted.isSuccess()) {
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
        return persisted;
    }

    @Override
    public Result<PublicMarketingFileUploadStatusVO> upload(String publicId, Long rawDraftId,
                                                             String uploadCapability,
                                                             String rawFieldCode, Long rawFileId,
                                                             Long version, AttachmentByteSource source) {
        Result<UploadSubject> admitted = commandTemplate.execute(UseCaseOperation.MARKETING_FILE_CONTENT_UPLOAD,
                new ServiceOperation<>() {
                    private MarketingFormPublicId.Parts parts;
                    @Override public void validate() {
                        parts = parts(publicId);
                        if (source == null || version == null || version < 0) throw invalid("marketing file upload is invalid");
                    }
                    @Override public UploadSubject execute() {
                        MarketingForm form = activeFormForUpdate(parts);
                        objectLock.acquire(parts.tenantId(), BusinessObjectType.LEAD);
                        var validated = formPolicy.validate(parts.tenantId(), form.getOwnerMemberId(), form.getFields(), true);
                        MarketingFileDraft draft = lockedDraft(parts, form, rawDraftId, uploadCapability, true);
                        FieldDefinition field = publicFileField(parts.tenantId(), form, validated.definitions(), rawFieldCode);
                        AttachmentFileId fileId = fileId(rawFileId);
                        var staged = staged(parts.tenantId(), draft, field.getCode(), fileId);
                        AttachmentFile file = staged.file();
                        if (!file.getUploadOrigin().equals(new io.github.archetom.crm.domain.model.AttachmentUploadOrigin.PublicMarketingForm(draft.getId()))
                                || source.declaredLength() != file.getExpectedBytes()) throw unavailableError();
                        AttachmentWorkStore.ContentPlan plan = workStore.findContentPlan(parts.tenantId(), fileId)
                                .orElseThrow(MarketingFileDraftServiceImpl::unavailableError);
                        AttachmentStorageReadinessResolver.Snapshot readiness = storageReadinessResolver.resolve(
                                parts.tenantId(), AttachmentOperation.STORE_SINGLE_STREAM);
                        if (!ready(readiness, AttachmentOperation.STORE_SINGLE_STREAM)
                                || !java.util.Arrays.equals(readiness.binding().copyValue(), plan.binding().copyValue())) {
                            throw attachmentSupport.unavailable();
                        }
                        if (file.getByteState() == AttachmentByteState.STORED) {
                            if (!version.equals(plan.intentVersion()) || plan.evidence() == null || plan.storedIdentity() == null) {
                                throw attachmentSupport.conflict();
                            }
                        } else if (!version.equals(file.getVersion()) || file.getByteState() != AttachmentByteState.UPLOADING
                                || file.getPublicationState() != AttachmentPublicationState.PRIVATE
                                || !clock.instant().isBefore(plan.deadline())) {
                            throw unavailableError();
                        }
                        return new UploadSubject(parts, draft.getId(), field.getCode(), fileId, file, plan, readiness);
                    }
                });
        if (!admitted.isSuccess()) return copyFailure(admitted);
        UploadSubject subject = admitted.getData();
        AttachmentStorage.StoreReceipt receipt;
        try {
            receipt = storage.store(new AttachmentStorage.StoreCommand(subject.plan().binding(), subject.plan().locator(),
                    subject.plan().storageGeneration(), subject.file().getContentVersion(), subject.plan().attempt(),
                    subject.file().getExpectedBytes(), subject.file().getExpectedSha256(), deadline(subject.plan(), subject.file()),
                    runtimePolicy.uploadOpenTimeout(), runtimePolicy.uploadIdleTimeout()), source);
        } catch (AttachmentStorage.StoreConflictException exception) { return conflict(); }
        catch (Exception exception) {
            publicRuntimeStatus.recordRuntimeFailure();
            return unavailable(UseCaseOperation.MARKETING_FILE_CONTENT_UPLOAD);
        }
        if (receipt == null || receipt.identity() == null || receipt.evidence() == null) {
            publicRuntimeStatus.recordRuntimeFailure();
            return unavailable(UseCaseOperation.MARKETING_FILE_CONTENT_UPLOAD);
        }
        publicRuntimeStatus.recordAdapterSuccess();
        return commandTemplate.execute(UseCaseOperation.MARKETING_FILE_CONTENT_UPLOAD, new ServiceOperation<>() {
            @Override public void validate() { }
            @Override public PublicMarketingFileUploadStatusVO execute() {
                MarketingForm form = activeFormForUpdate(subject.parts());
                objectLock.acquire(subject.parts().tenantId(), BusinessObjectType.LEAD);
                var validated = formPolicy.validate(subject.parts().tenantId(), form.getOwnerMemberId(), form.getFields(), true);
                MarketingFileDraft draft = lockedDraft(subject.parts(), form, subject.draftId().getValue(),
                        uploadCapability, true);
                FieldDefinition field = publicFileField(subject.parts().tenantId(), form, validated.definitions(),
                        subject.fieldCode().getValue());
                AttachmentFile file = staged(subject.parts().tenantId(), draft, field.getCode(), subject.fileId()).file();
                AttachmentWorkStore.ContentPlan plan = workStore.findContentPlan(subject.parts().tenantId(), subject.fileId())
                        .orElseThrow(MarketingFileDraftServiceImpl::unavailableError);
                if (!version.equals(plan.intentVersion()) || plan.contentVersion() != file.getContentVersion()
                        || !java.util.Arrays.equals(plan.storageGeneration(), subject.plan().storageGeneration())) {
                    throw attachmentSupport.conflict();
                }
                if (file.getByteState() == AttachmentByteState.STORED) {
                    if (plan.evidence() == null || plan.storedIdentity() == null
                            || plan.evidence().actualBytes() != receipt.evidence().actualBytes()
                            || !java.util.Arrays.equals(plan.evidence().sha256(), receipt.evidence().sha256())
                            || !java.util.Arrays.equals(plan.storedIdentity().copyValue(), receipt.identity().copyValue())) {
                        throw attachmentSupport.conflict();
                    }
                    return publicStatus(draft, field.getCode(), file);
                }
                if (file.getByteState() != AttachmentByteState.UPLOADING
                        || file.getPublicationState() != AttachmentPublicationState.PRIVATE) throw unavailableError();
                file.acceptStored(receipt.evidence(), file.getDeclaredMediaType(), local(clock.instant()));
                fileRepository.save(subject.parts().tenantId(), file);
                draft.settleActual(receipt.evidence().actualBytes(), publicProperties.getMaxBytesPerDraft(), local(clock.instant()));
                draftRepository.save(subject.parts().tenantId(), draft);
                workStore.recordStored(subject.parts().tenantId(), subject.fileId(), file.getContentVersion(), plan.attempt(),
                        receipt.identity(), receipt.evidence(), clock.instant());
                if (!workStore.reserveUsage(subject.parts().tenantId(), new AttachmentWorkStore.UsageDelta(
                        0, receipt.evidence().actualBytes(), -file.getExpectedBytes(), -1, -1, 1, 0),
                        runtimePolicy.usageLimits(), clock.instant())) throw attachmentSupport.unavailable();
                workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.parts().tenantId(), subject.fileId(),
                        file.getContentVersion(), AttachmentWorkStore.JobKind.INSPECTION, 1, clock.instant(),
                        clock.instant().plus(runtimePolicy.inspectionTotalTimeout())));
                return publicStatus(draft, field.getCode(), file);
            }
        });
    }

    @Override
    public Result<PublicMarketingFileUploadStatusVO> uploadStatus(String publicId, Long rawDraftId,
                                                                    String uploadCapability, String rawFieldCode,
                                                                    Long rawFileId) {
        return commandTemplate.execute(UseCaseOperation.MARKETING_FILE_UPLOAD_STATUS_GET, new ServiceOperation<>() {
            private MarketingFormPublicId.Parts parts;
            @Override public void validate() { parts = parts(publicId); }
            @Override public PublicMarketingFileUploadStatusVO execute() {
                MarketingForm form = activeFormForUpdate(parts);
                objectLock.acquire(parts.tenantId(), BusinessObjectType.LEAD);
                var validated = formPolicy.validate(parts.tenantId(), form.getOwnerMemberId(), form.getFields(), true);
                MarketingFileDraft draft = draftRepository.findForUpdate(parts.tenantId(), draftId(rawDraftId))
                        .orElseThrow(MarketingFileDraftServiceImpl::unavailableError);
                MarketingFileDraftCredential.requireMatches(uploadCapability, draft);
                if (draft.expire(local(clock.instant()))) {
                    cleanupDraft(parts.tenantId(), draft);
                    throw unavailableError();
                }
                if (!draft.isActiveFor(form.getId(), form.getVersion(), local(clock.instant()))) throw unavailableError();
                FieldDefinition field = publicFileField(parts.tenantId(), form, validated.definitions(), rawFieldCode);
                AttachmentFile file = staged(parts.tenantId(), draft, field.getCode(), fileId(rawFileId)).file();
                return publicStatus(draft, field.getCode(), file);
            }
        });
    }

    @Override
    public Result<PublicMarketingFileDraftWithdrawalVO> withdraw(String publicId, Long rawDraftId,
                                                                  String uploadCapability) {
        return commandTemplate.execute(UseCaseOperation.MARKETING_FILE_DRAFT_WITHDRAW, new ServiceOperation<>() {
            private MarketingFormPublicId.Parts parts;
            @Override public void validate() { parts = parts(publicId); }
            @Override public PublicMarketingFileDraftWithdrawalVO execute() {
                // Match every public FILE mutation: form -> LEAD customization guard -> draft.
                // Withdrawal intentionally accepts an inactive form so abandoned candidates can
                // still be cleaned up, but it must lock that exact public root first.
                MarketingForm form = formRepository.findByPublicKeyForUpdate(parts.tenantId(), parts.publicKey())
                        .orElseThrow(MarketingFileDraftServiceImpl::unavailableError);
                objectLock.acquire(parts.tenantId(), BusinessObjectType.LEAD);
                MarketingFileDraft draft = draftRepository.findForUpdate(parts.tenantId(), draftId(rawDraftId))
                        .orElseThrow(MarketingFileDraftServiceImpl::unavailableError);
                MarketingFileDraftCredential.requireMatches(uploadCapability, draft);
                if (!draft.getFormId().sameValueAs(form.getId())) throw unavailableError();
                if (draft.expire(local(clock.instant())) || draft.getState() == io.github.archetom.crm.domain.model.MarketingFileDraftState.EXPIRED) {
                    cleanupDraft(parts.tenantId(), draft);
                    return new PublicMarketingFileDraftWithdrawalVO(draft.getId().getValue(), true);
                }
                draft.withdraw(local(clock.instant()));
                cleanupDraft(parts.tenantId(), draft);
                return new PublicMarketingFileDraftWithdrawalVO(draft.getId().getValue(), true);
            }
        });
    }

    @Override
    public PreparedSubmission prepareSubmission(TenantId tenantId, MarketingForm form, String authorization,
                                                Long rawDraftId, Map<String, List<Long>> fileFields,
                                                Map<FieldCode, FieldDefinition> definitions) {
        Map<FieldCode, List<Long>> requested = requested(form, definitions, fileFields);
        requireRequired(form, requested);
        if (rawDraftId == null) {
            if (!requested.isEmpty()) throw unavailableError();
            return new PreparedSubmission(null, List.of(), List.of());
        }
        MarketingFileDraft draft = draftRepository.findForUpdate(tenantId, draftId(rawDraftId))
                .orElseThrow(MarketingFileDraftServiceImpl::unavailableError);
        MarketingFileDraftCredential.requireMatches(authorization, draft);
        if (!draft.isActiveFor(form.getId(), form.getVersion(), local(clock.instant()))) {
            if (draft.expire(local(clock.instant()))) cleanupDraft(tenantId, draft);
            throw unavailableError();
        }
        List<MarketingFileDraftRepository.StagedFile> staged = draftRepository.findStagedFilesForUpdate(tenantId, draft.getId());
        requireDraftOrigins(draft, staged);
        List<FileFieldValue> values = values(tenantId, requested, staged);
        return new PreparedSubmission(draft, values, staged);
    }

    @Override
    public void consumePrepared(PreparedSubmission prepared, Long rawLeadId) {
        if (prepared == null || prepared.draft() == null) return;
        if (rawLeadId == null || rawLeadId <= 0) throw new IllegalStateException("persisted lead identity is unavailable");
        TenantId tenantId = prepared.draft().getTenantId();
        LocalDateTime now = local(clock.instant());
        requireDraftOrigins(prepared.draft(), prepared.staged());
        for (FileFieldValue value : prepared.values()) {
            AttachmentFile file = prepared.staged().stream().filter(candidate -> candidate.file().getId().sameValueAs(value.fileId()))
                    .map(MarketingFileDraftRepository.StagedFile::file).findFirst()
                    .orElseThrow(MarketingFileDraftServiceImpl::unavailableError);
            file.publish(now);
            fileRepository.save(tenantId, file);
        }
        Map<FieldCode, List<FileFieldValue>> grouped = prepared.values().stream().collect(Collectors.groupingBy(
                FileFieldValue::fieldCode, LinkedHashMap::new, Collectors.toList()));
        fileFieldRepository.replaceCrm(tenantId,
                new io.github.archetom.crm.domain.model.AttachmentTarget(
                        io.github.archetom.crm.domain.model.AttachmentTargetType.LEAD, rawLeadId), grouped);
        draftRepository.markStagedBound(tenantId, prepared.draft().getId(), prepared.values());
        prepared.draft().consume(now);
        draftRepository.save(tenantId, prepared.draft());
    }

    private MarketingForm activeFormForUpdate(MarketingFormPublicId.Parts parts) {
        return formRepository.findActiveByPublicKeyForUpdate(parts.tenantId(), parts.publicKey())
                .filter(form -> form.getTargetType() == MarketingFormTargetType.LEAD)
                .orElseThrow(MarketingFileDraftServiceImpl::unavailableError);
    }

    private MarketingFileDraft lockedDraft(MarketingFormPublicId.Parts parts, MarketingForm form,
                                            Long rawDraftId, String authorization, boolean activeRequired) {
        MarketingFileDraft draft = draftRepository.findForUpdate(parts.tenantId(), draftId(rawDraftId))
                .orElseThrow(MarketingFileDraftServiceImpl::unavailableError);
        MarketingFileDraftCredential.requireMatches(authorization, draft);
        LocalDateTime now = local(clock.instant());
        if (draft.expire(now)) cleanupDraft(parts.tenantId(), draft);
        if (!draft.getFormId().sameValueAs(form.getId()) || draft.getFormVersion() != form.getVersion()
                || activeRequired && !draft.isActiveFor(form.getId(), form.getVersion(), now)) {
            throw unavailableError();
        }
        return draft;
    }

    private FieldDefinition publicFileField(TenantId tenantId, MarketingForm form,
                                            Map<FieldCode, FieldDefinition> definitions, String rawCode) {
        FieldCode code;
        try { code = new FieldCode(rawCode); } catch (RuntimeException exception) { throw unavailableError(); }
        if (form.getFields().stream().noneMatch(value -> value.fieldCode().equals(code))) throw unavailableError();
        FieldDefinition definition = definitions.get(code);
        if (definition == null || definition.isBuiltin() || !definition.isActive()
                || definition.getFieldType() != CustomFieldType.FILE) throw unavailableError();
        return definition;
    }

    private void requireConfiguredFiles(MarketingForm form, Map<FieldCode, FieldDefinition> definitions) {
        if (form.getFields().stream().noneMatch(value -> {
            FieldDefinition definition = definitions.get(value.fieldCode());
            return definition != null && !definition.isBuiltin() && definition.getFieldType() == CustomFieldType.FILE;
        })) throw unavailableError();
    }

    private List<String> publicFileCodes(MarketingForm form,
                                         Map<FieldCode, FieldDefinition> definitions) {
        return form.getFields().stream().filter(value -> {
            FieldDefinition definition = definitions.get(value.fieldCode());
            return definition != null && !definition.isBuiltin() && definition.isActive()
                    && definition.getFieldType() == CustomFieldType.FILE;
        }).map(value -> value.fieldCode().getValue()).toList();
    }

    private Map<FieldCode, List<Long>> requested(MarketingForm form,
                                                   Map<FieldCode, FieldDefinition> definitions,
                                                   Map<String, List<Long>> source) {
        Map<String, List<Long>> raw = source == null ? Map.of() : source;
        LinkedHashMap<FieldCode, List<Long>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Long>> entry : raw.entrySet()) {
            FieldCode code;
            try { code = new FieldCode(entry.getKey()); } catch (RuntimeException exception) { throw unavailableError(); }
            FieldDefinition definition = publicFileField(null, form, definitions, code.getValue());
            List<Long> rawIds = entry.getValue() == null ? List.of() : entry.getValue();
            if (rawIds.stream().anyMatch(Objects::isNull)) {
                throw invalid("marketing form file fields are invalid");
            }
            List<Long> ids = List.copyOf(rawIds);
            if (ids.size() > publicProperties.getMaxFilesPerDraft() || ids.stream().anyMatch(value -> value == null || value <= 0)
                    || new LinkedHashSet<>(ids).size() != ids.size()) throw unavailableError();
            result.put(definition.getCode(), ids);
        }
        return result;
    }

    private void requireRequired(MarketingForm form, Map<FieldCode, List<Long>> values) {
        for (var field : form.getFields()) {
            if (!field.required()) continue;
            FieldDefinition definition = fieldRepository.findByCode(form.getTenantId(), BusinessObjectType.LEAD, field.fieldCode())
                    .orElse(null);
            if (definition != null && definition.getFieldType() == CustomFieldType.FILE
                    && values.getOrDefault(field.fieldCode(), List.of()).isEmpty()) throw unavailableError();
        }
    }

    private List<FileFieldValue> values(TenantId tenantId, Map<FieldCode, List<Long>> requested,
                                        List<MarketingFileDraftRepository.StagedFile> staged) {
        Map<AttachmentFileId, MarketingFileDraftRepository.StagedFile> byFile = staged.stream().collect(Collectors.toMap(
                value -> value.file().getId(), Function.identity()));
        List<FileFieldValue> values = new ArrayList<>();
        Set<AttachmentFileId> selected = new LinkedHashSet<>();
        for (Map.Entry<FieldCode, List<Long>> entry : requested.entrySet()) {
            int ordinal = 0;
            for (Long rawId : entry.getValue()) {
                AttachmentFileId fileId = fileId(rawId);
                MarketingFileDraftRepository.StagedFile candidate = byFile.get(fileId);
                if (candidate == null || !candidate.field().getCode().equals(entry.getKey()) || !selected.add(fileId)) {
                    throw unavailableError();
                }
                AttachmentFile file = candidate.file();
                if (file.getByteState() != AttachmentByteState.STORED || file.getInspectionState() != AttachmentInspectionState.CLEAN
                        || file.getPublicationState() != AttachmentPublicationState.PRIVATE) throw unavailableError();
                AttachmentWorkStore.ContentPlan plan = workStore.findContentPlan(tenantId, fileId)
                        .filter(value -> value.contentVersion() == file.getContentVersion() && value.evidence() != null)
                        .orElseThrow(MarketingFileDraftServiceImpl::unavailableError);
                values.add(new FileFieldValue(entry.getKey(), fileId, ordinal++, file.getContentVersion(),
                        plan.evidence().sha256()));
            }
        }
        if (selected.size() != staged.size()) throw unavailableError();
        return List.copyOf(values);
    }

    private MarketingFileDraftRepository.StagedFile staged(TenantId tenantId, MarketingFileDraft draft,
                                                            FieldCode fieldCode, AttachmentFileId fileId) {
        return draftRepository.findStagedFilesForUpdate(tenantId, draft.getId()).stream()
                .filter(value -> value.field().getCode().equals(fieldCode) && value.file().getId().sameValueAs(fileId))
                .findFirst().orElseThrow(MarketingFileDraftServiceImpl::unavailableError);
    }

    private void cleanupDraft(TenantId tenantId, MarketingFileDraft draft) {
        List<MarketingFileDraftRepository.StagedFile> candidates = draftRepository.findStagedFilesForUpdate(tenantId, draft.getId());
        draftRepository.withdrawStagedFiles(tenantId, draft.getId());
        Instant instant = clock.instant();
        LocalDateTime now = local(instant);
        for (MarketingFileDraftRepository.StagedFile candidate : candidates) {
            AttachmentFile file = candidate.file();
            file.withdraw(now);
            fileRepository.save(tenantId, file);
            AttachmentWorkStore.JobKind cleanup = file.getByteState() == AttachmentByteState.REMOVING
                    ? AttachmentWorkStore.JobKind.DELETION : AttachmentWorkStore.JobKind.INCOMPLETE_ABORT;
            Instant deadline = instant.plus(runtimePolicy.tombstoneRetention());
            workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, file.getId(),
                    file.getContentVersion(), cleanup, 1, instant, deadline));
            workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, file.getId(),
                    file.getContentVersion(), AttachmentWorkStore.JobKind.RETENTION_SWEEP,
                    3, deadline, deadline.plus(runtimePolicy.tombstoneRetention())));
        }
        draftRepository.save(tenantId, draft);
    }

    private void requireAdmissionEnabled() {
        if (!publicProperties.isEnabled() || !publicProperties.valid()) {
            throw attachmentSupport.unavailable();
        }
        if (!runtimePolicy.enabled()) {
            publicRuntimeStatus.recordFilesNotReady();
            throw attachmentSupport.unavailable();
        }
    }

    private void requireNewDraftReadiness(TenantId tenantId) {
        Instant now = clock.instant();
        var inspection = inspectionReadinessResolver.resolve(tenantId);
        AttachmentStorageReadinessResolver.Snapshot storage = storageReadinessResolver.resolve(
                tenantId, AttachmentOperation.ALLOCATE_WRITE);
        boolean inspectionReady = inspection != null && inspection.readiness() == AttachmentReadiness.READY
                && inspection.allowedMediaTypes() != null && !inspection.allowedMediaTypes().isEmpty()
                && inspection.expiresAt() != null && inspection.expiresAt().isAfter(now);
        if (!inspectionReady || !ready(storage, AttachmentOperation.ALLOCATE_WRITE)
                || !ready(storage, AttachmentOperation.STORE_SINGLE_STREAM)
                || !ready(storage, AttachmentOperation.PROBE_EXACT)
                || !ready(storage, AttachmentOperation.ABORT_INCOMPLETE)) {
            publicRuntimeStatus.recordFilesNotReady();
            throw attachmentSupport.unavailable();
        }
        publicRuntimeStatus.recordReadinessReady();
    }

    private void requireDraftOrigins(MarketingFileDraft draft,
                                     List<MarketingFileDraftRepository.StagedFile> candidates) {
        if (draft == null || candidates == null || candidates.stream().anyMatch(candidate -> candidate == null
                || candidate.file() == null || !(candidate.file().getUploadOrigin()
                instanceof io.github.archetom.crm.domain.model.AttachmentUploadOrigin.PublicMarketingForm origin)
                || !origin.draftId().sameValueAs(draft.getId()))) {
            throw unavailableError();
        }
    }

    private boolean ready(AttachmentStorageReadinessResolver.Snapshot value, AttachmentOperation operation) {
        return value != null && value.isReady(operation, clock.instant());
    }
    private static MarketingFormPublicId.Parts parts(String value) {
        return MarketingFormPublicId.parse(value).orElseThrow(MarketingFileDraftServiceImpl::unavailableError);
    }
    private static MarketingFileDraftId draftId(Long value) { try { return new MarketingFileDraftId(value); }
        catch (RuntimeException exception) { throw unavailableError(); } }
    private static AttachmentFileId fileId(Long value) { try { return new AttachmentFileId(value); }
        catch (RuntimeException exception) { throw unavailableError(); } }
    private Instant deadline(AttachmentWorkStore.ContentPlan plan, AttachmentFile file) {
        Instant total = clock.instant().plus(runtimePolicy.uploadTotalTimeout());
        return file.getByteState() == AttachmentByteState.STORED ? total
                : plan.deadline().isBefore(total) ? plan.deadline() : total;
    }
    static Instant noLaterThan(Instant value, Instant limit) {
        return value.isAfter(limit) ? limit : value;
    }

    static Instant retentionDeadline(Instant firstRetention, java.time.Duration tombstoneRetention) {
        if (firstRetention == null || tombstoneRetention == null || tombstoneRetention.isNegative()
                || tombstoneRetention.isZero()) {
            throw new IllegalArgumentException("marketing file retention deadline is invalid");
        }
        return firstRetention.plus(tombstoneRetention);
    }
    private static PublicMarketingFileUploadStatusVO publicStatus(MarketingFileDraft draft,
                                                                    FieldCode fieldCode,
                                                                    AttachmentFile file) {
        String status = file.getInspectionState() == AttachmentInspectionState.REJECTED
                || file.getPublicationState() == AttachmentPublicationState.WITHDRAWN
                || file.getPublicationState() == AttachmentPublicationState.DELETED
                || file.getByteState() == AttachmentByteState.REMOVING
                || file.getByteState() == AttachmentByteState.REMOVED ? "FAILED"
                : file.getByteState() == AttachmentByteState.STORED
                && file.getInspectionState() == AttachmentInspectionState.CLEAN ? "UPLOADED"
                : "PENDING";
        String mediaType = file.getMediaType() == null
                ? file.getDeclaredMediaType() : file.getMediaType();
        long size = file.getActualBytes() > 0 ? file.getActualBytes() : file.getExpectedBytes();
        return new PublicMarketingFileUploadStatusVO(draft.getId().getValue(), fieldCode.getValue(),
                file.getId().getValue(), file.getDisplayName(), mediaType, size, file.getVersion(), status);
    }
    private <T> Result<T> unavailable(UseCaseOperation operation) {
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            @Override public void validate() { throw attachmentSupport.unavailable(); }
            @Override public T execute() { throw new IllegalStateException(); }
        });
    }
    private <T> Result<T> conflict() {
        return commandTemplate.execute(UseCaseOperation.MARKETING_FILE_CONTENT_UPLOAD, new ServiceOperation<>() {
            @Override public void validate() { throw attachmentSupport.conflict(); }
            @Override public T execute() { throw new IllegalStateException(); }
        });
    }
    @SuppressWarnings("unchecked") private static <T> Result<T> copyFailure(Result<?> value) { return (Result<T>) value; }
    private static LocalDateTime local(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, message);
    }
    private static NonRetryableApplicationException unavailableError() {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                "Marketing file draft is unavailable");
    }

    private record IntentAdmission(MarketingFormPublicId.Parts parts, Long formId,
                                   MarketingFileDraftId draftId, FieldDefinition field, String displayName,
                                   String mediaType, byte[] expectedSha, long expectedBytes,
                                   AttachmentStorageReadinessResolver.Snapshot readiness, Instant now,
                                   Instant draftDeadline) { }
    private record UploadSubject(MarketingFormPublicId.Parts parts, MarketingFileDraftId draftId,
                                 FieldCode fieldCode, AttachmentFileId fileId, AttachmentFile file,
                                 AttachmentWorkStore.ContentPlan plan,
                                 AttachmentStorageReadinessResolver.Snapshot readiness) { }
}
