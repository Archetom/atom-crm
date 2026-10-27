package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.request.FileFieldDraftCreateRequest;
import io.github.archetom.crm.api.dto.request.OaFileFieldDraftCreateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentInspectionReadinessResolver;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.port.out.attachment.AttachmentOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorageReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.PreCreateFileFieldDraftAuthorizer;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccess;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccessRegistry;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetOperation;
import io.github.archetom.crm.application.service.FileFieldDraftService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.AttachmentUploadIntentVO;
import io.github.archetom.crm.application.vo.FileFieldDraftVO;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FileFieldDraft;
import io.github.archetom.crm.domain.entity.OaFormDefinition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.ApprovalDefinitionRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.repository.OaFormRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FileFieldDraftId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** B24c draft admission and staged-upload-intent creation; byte streaming stays in its own service. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileFieldDraftServiceImpl implements FileFieldDraftService {
    private final FileFieldRepository draftRepository;
    private final AttachmentFileRepository fileRepository;
    private final FieldDefinitionRepository fieldRepository;
    private final OaFormRepository formRepository;
    private final ApprovalDefinitionRepository approvalDefinitionRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final AttachmentWorkStore workStore;
    private final AttachmentStorage storage;
    private final AttachmentStorageReadinessResolver storageReadinessResolver;
    private final AttachmentInspectionReadinessResolver inspectionReadinessResolver;
    private final AttachmentRuntimePolicy runtimePolicy;
    private final OrganizationAccessGuard accessGuard;
    private final PreCreateFileFieldDraftAuthorizer preCreateDraftAuthorizer;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final AttachmentTargetAccessRegistry targetAccessRegistry;
    private final CustomizationObjectLock objectLock;
    private final AttachmentApplicationSupport attachmentSupport;
    private final CommandServiceTemplate commandTemplate;
    private final Clock clock;

    @Override
    public Result<FileFieldDraftVO> createCrmDraft(
            AuthenticatedCaller caller, FileFieldDraftCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.ATTACHMENT_UPLOAD_INTENT_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId; private MemberId memberId; private BusinessObjectType objectType;
                    @Override public void validate() {
                        if (request == null) throw invalid("file field draft request is required");
                        tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
                        memberId = new MemberId(caller.actorId());
                        objectType = crmObjectType(request.getObjectType());
                        preCreateDraftAuthorizer.requireCurrentWrite(caller, tenantId, memberId, objectType);
                    }
                    @Override public FileFieldDraftVO execute() {
                        objectLock.acquire(tenantId, objectType);
                        var accesses = fieldAccessResolver.resolve(tenantId, memberId, objectType);
                        boolean writableFile = fieldRepository.findAll(tenantId, objectType, false).stream()
                                .filter(FieldDefinition::isActive)
                                .filter(value -> value.getFieldType() == CustomFieldType.FILE)
                                .map(value -> accesses.get(value.getCode()))
                                .anyMatch(access -> access != null
                                        && access.accessLevel().covers(FieldAccessLevel.WRITE));
                        if (!writableFile) throw invalid("CRM object has no writable file field");
                        LocalDateTime now = local(clock.instant());
                        FileFieldDraft draft = FileFieldDraft.create(tenantId, memberId, objectType, null,
                                null, null, digest(tenantId, memberId, objectType, null),
                                local(clock.instant().plus(runtimePolicy.intentTtl())), now);
                        draftRepository.saveDraft(tenantId, draft);
                        return vo(draft);
                    }
                });
    }

    @Override
    public Result<FileFieldDraftVO> createStageWorkflowDraft(
            AuthenticatedCaller caller, Long rawWorkflowRecordId, String rawObjectType) {
        return commandTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_FILE_DRAFT_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private BusinessObjectType objectType;
                    private StageWorkflowRecordId workflowRecordId;

                    @Override public void validate() {
                        tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
                        memberId = new MemberId(caller.actorId());
                        objectType = crmObjectType(rawObjectType);
                        try {
                            workflowRecordId = new StageWorkflowRecordId(rawWorkflowRecordId);
                        } catch (RuntimeException exception) {
                            throw invalid("stage workflow record ID is invalid");
                        }
                        preCreateDraftAuthorizer.requireCurrentWrite(caller, tenantId, memberId, objectType);
                    }

                    @Override public FileFieldDraftVO execute() {
                        objectLock.acquire(tenantId, objectType);
                        var accesses = fieldAccessResolver.resolve(tenantId, memberId, objectType);
                        boolean writableFile = fieldRepository.findAll(tenantId, objectType, false).stream()
                                .filter(FieldDefinition::isActive)
                                .filter(value -> value.getFieldType() == CustomFieldType.FILE)
                                .map(value -> accesses.get(value.getCode()))
                                .anyMatch(access -> access != null
                                        && access.accessLevel().covers(FieldAccessLevel.WRITE));
                        if (!writableFile) throw invalid("CRM object has no writable file field");
                        LocalDateTime now = local(clock.instant());
                        FileFieldDraft draft = FileFieldDraft.createStageWorkflow(tenantId, memberId,
                                objectType, workflowRecordId,
                                digest(tenantId, memberId, objectType, null),
                                local(clock.instant().plus(runtimePolicy.intentTtl())), now);
                        draftRepository.saveDraft(tenantId, draft);
                        return vo(draft);
                    }
                });
    }

    @Override
    public Result<FileFieldDraftVO> createOaDraft(
            AuthenticatedCaller caller, Long rawFormId, OaFileFieldDraftCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.ATTACHMENT_UPLOAD_INTENT_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId; private MemberId memberId; private OaFormId formId;
                    @Override public void validate() {
                        if (request == null || request.getFormVersion() == null
                                || request.getFormVersion() < 0) {
                            throw invalid("OA file field draft request is invalid");
                        }
                        tenantId = accessGuard.requireActiveMembership(caller,
                                AuthorityCatalog.OA_REQUESTS_SUBMIT);
                        memberId = new MemberId(caller.actorId());
                        try { formId = new OaFormId(rawFormId); }
                        catch (RuntimeException exception) { throw invalid("OA form ID is invalid"); }
                    }
                    @Override public FileFieldDraftVO execute() {
                        objectLock.acquire(tenantId, BusinessObjectType.OA_REQUEST);
                        OaFormDefinition form = formRepository.findByIdForUpdate(tenantId, formId)
                                .filter(OaFormDefinition::isActive)
                                .orElseThrow(() -> invalid("OA form is unavailable"));
                        if (!request.getFormVersion().equals(form.getVersion())) {
                            throw invalid("OA form version is invalid");
                        }
                        OrganizationMembership membership = membershipRepository.findByMember(tenantId, memberId)
                                .filter(OrganizationMembership::isActive)
                                .orElseThrow(() -> new NonRetryableApplicationException(
                                        ApplicationErrorCode.ACCESS_DENIED,
                                        "Caller is not an active tenant member"));
                        ApprovalDefinition definition = approvalDefinitionRepository.findByIdForUpdate(
                                        tenantId, form.getApprovalDefinitionId())
                                .filter(value -> value.getObjectType() == BusinessObjectType.OA_REQUEST)
                                .filter(ApprovalDefinition::isActive)
                                .orElseThrow(() -> invalid("OA approval definition is unavailable"));
                        if (!definition.isApplicantAllowed(memberId, membership.getDepartmentId())) {
                            throw new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED,
                                    "Caller is outside the OA approval scope");
                        }
                        var accesses = fieldAccessResolver.resolve(tenantId, memberId,
                                BusinessObjectType.OA_REQUEST);
                        List<FieldDefinition> configured = form.getFields().stream().map(value ->
                                fieldRepository.findByCode(tenantId, BusinessObjectType.OA_REQUEST,
                                                value.fieldCode())
                                        .filter(FieldDefinition::isActive)
                                        .orElseThrow(() -> invalid("OA request field is unavailable")))
                                .toList();
                        if (configured.stream().anyMatch(value -> {
                            var access = accesses.get(value.getCode());
                            return access == null || !access.accessLevel().covers(FieldAccessLevel.WRITE);
                        })) {
                            throw new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED,
                                    "OA request field is not writable");
                        }
                        if (configured.stream().noneMatch(value -> value.getFieldType() == CustomFieldType.FILE)) {
                            throw invalid("OA form has no writable file field");
                        }
                        LocalDateTime now = local(clock.instant());
                        FileFieldDraft draft = FileFieldDraft.create(tenantId, memberId,
                                BusinessObjectType.OA_REQUEST, null, form.getId(), form.getVersion(),
                                digest(tenantId, memberId, BusinessObjectType.OA_REQUEST, null),
                                local(clock.instant().plus(runtimePolicy.intentTtl())), now);
                        draftRepository.saveDraft(tenantId, draft);
                        return vo(draft);
                    }
                });
    }

    @Override
    public Result<AttachmentUploadIntentVO> createDraftIntent(
            AuthenticatedCaller caller, Long rawDraftId, String fieldCode,
            AttachmentUploadIntentCreateRequest request) {
        return createIntent(caller, rawDraftId, null, fieldCode, request);
    }

    @Override
    public Result<AttachmentUploadIntentVO> createDirectIntent(
            AuthenticatedCaller caller, String targetType, Long targetId, String fieldCode,
            AttachmentUploadIntentCreateRequest request) {
        AttachmentTarget target = target(targetType, targetId);
        return createIntent(caller, null, target, fieldCode, request);
    }

    @Override
    public Result<Void> withdraw(AuthenticatedCaller caller, Long rawDraftId, Long version) {
        return commandTemplate.execute(UseCaseOperation.ATTACHMENT_WITHDRAW, new ServiceOperation<>() {
            private TenantId tenantId; private MemberId memberId; private FileFieldDraftId draftId;
            @Override public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
                memberId = new MemberId(caller.actorId());
                draftId = draftId(rawDraftId);
                if (version == null || version < 0) throw invalid("file field draft version is invalid");
            }
            @Override public Void execute() {
                FileFieldDraft snapshot = draftRepository.findDraft(tenantId, draftId)
                        .orElseThrow(() -> invalid("file field draft is unavailable"));
                objectLock.acquire(tenantId, snapshot.getObjectType());
                verifyDirectTarget(caller, tenantId, snapshot.getDirectTarget(),
                        AttachmentTargetOperation.WITHDRAW);
                FileFieldDraft draft = draftRepository.findDraftForUpdate(tenantId, draftId)
                        .orElseThrow(() -> invalid("file field draft is unavailable"));
                if (!version.equals(draft.getVersion())) throw invalid("file field draft version is invalid");
                if (!java.util.Objects.equals(snapshot.getDirectTarget(), draft.getDirectTarget())) {
                    throw attachmentSupport.notFound();
                }
                Instant now = clock.instant();
                List<FileFieldRepository.StagedFile> stagedFiles = draftRepository.findStagedFilesForUpdate(
                        tenantId, draftId);
                var accesses = fieldAccessResolver.resolve(tenantId, memberId, draft.getObjectType());
                if (stagedFiles.stream().anyMatch(staged -> {
                    var access = accesses.get(staged.field().getCode());
                    return access == null || !access.accessLevel().covers(FieldAccessLevel.WRITE);
                })) {
                    throw new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED,
                            "file field is not writable");
                }
                draft.withdraw(memberId, local(now));
                draftRepository.saveDraft(tenantId, draft);
                draftRepository.withdrawStagedFiles(tenantId, draftId);
                for (FileFieldRepository.StagedFile staged : stagedFiles) {
                    AttachmentFile file = staged.file();
                    file.withdraw(local(now));
                    fileRepository.save(tenantId, file);
                    AttachmentWorkStore.JobKind cleanup = file.getByteState()
                            == io.github.archetom.crm.domain.model.AttachmentByteState.REMOVING
                            ? AttachmentWorkStore.JobKind.DELETION
                            : AttachmentWorkStore.JobKind.INCOMPLETE_ABORT;
                    Instant deadline = now.plus(runtimePolicy.tombstoneRetention());
                    workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, file.getId(),
                            file.getContentVersion(), cleanup, 1, now, deadline));
                    workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(tenantId, file.getId(),
                            file.getContentVersion(), AttachmentWorkStore.JobKind.RETENTION_SWEEP,
                            3, deadline, deadline.plus(runtimePolicy.tombstoneRetention())));
                }
                return null;
            }
        });
    }

    private Result<AttachmentUploadIntentVO> createIntent(AuthenticatedCaller caller, Long rawDraftId,
            AttachmentTarget directTarget, String rawFieldCode, AttachmentUploadIntentCreateRequest request) {
        Result<Admission> admission = commandTemplate.execute(UseCaseOperation.ATTACHMENT_UPLOAD_INTENT_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId; private MemberId memberId; private FileFieldDraft draft;
                    private FieldDefinition field; private String displayName; private String mediaType;
                    private OaFormDefinition expectedForm;
                    private byte[] expectedSha; private Instant now;
                    private AttachmentStorageReadinessResolver.Snapshot storageReadiness;
                    @Override public void validate() {
                        if (request == null || request.getExpectedBytes() == null) {
                            throw invalid("file field upload intent is invalid");
                        }
                        tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
                        memberId = new MemberId(caller.actorId()); now = clock.instant();
                        displayName = attachmentSupport.displayName(request.getDisplayName());
                        mediaType = attachmentSupport.declaredMediaType(request.getDeclaredMediaType(), displayName);
                        expectedSha = attachmentSupport.expectedSha256(request.getExpectedSha256());
                        if (!runtimePolicy.enabled() || request.getExpectedBytes() > runtimePolicy.maxFileBytes()) {
                            throw attachmentSupport.unavailable();
                        }
                        storageReadiness = storageReadinessResolver.resolve(tenantId, AttachmentOperation.ALLOCATE_WRITE);
                        var inspection = inspectionReadinessResolver.resolve(tenantId);
                        if (storageReadiness == null || !storageReadiness.isReady(AttachmentOperation.ALLOCATE_WRITE, now)
                                || !storageReadiness.isReady(AttachmentOperation.STORE_SINGLE_STREAM, now)
                                || !storageReadiness.isReady(AttachmentOperation.PROBE_EXACT, now)
                                || !storageReadiness.isReady(AttachmentOperation.ABORT_INCOMPLETE, now)
                                || inspection == null || !inspection.allows(mediaType, now)) {
                            throw attachmentSupport.unavailable();
                        }
                    }
                    @Override public Admission execute() {
                        if (directTarget != null) {
                            BusinessObjectType type = objectType(directTarget.type());
                            objectLock.acquire(tenantId, type);
                            var lifecycle = targetAccess(directTarget).lockForAttachmentMutation(caller,
                                    directTarget, AttachmentTargetOperation.CREATE_INTENT);
                            if (!tenantId.sameValueAs(lifecycle.tenantId()) || !lifecycle.attachmentsPermitted()) {
                                throw attachmentSupport.notFound();
                            }
                            draft = FileFieldDraft.create(tenantId, memberId, type, directTarget, null, null,
                                    digest(tenantId, memberId, type, directTarget),
                                    local(now.plus(runtimePolicy.intentTtl())), local(now));
                            draftRepository.saveDraft(tenantId, draft);
                        } else {
                            FileFieldDraft snapshot = draftRepository.findDraft(tenantId, draftId(rawDraftId))
                                    .orElseThrow(() -> invalid("file field draft is unavailable"));
                            verifyPreCreateDraftAuthorization(caller, tenantId, memberId, snapshot);
                            objectLock.acquire(tenantId, snapshot.getObjectType());
                            draft = draftRepository.findDraftForUpdate(tenantId, draftId(rawDraftId))
                                    .orElseThrow(() -> invalid("file field draft is unavailable"));
                            if (!draft.isActiveFor(memberId, draft.getObjectType(), local(now))) {
                                throw invalid("file field draft is unavailable");
                            }
                        }
                        if (draft.getObjectType() == BusinessObjectType.OA_REQUEST) {
                            expectedForm = formRepository.findByIdForUpdate(tenantId, draft.getOaFormId())
                                    .filter(OaFormDefinition::isActive)
                                    .filter(value -> value.getVersion().equals(draft.getOaFormVersion()))
                                    .orElseThrow(() -> invalid("OA form is unavailable"));
                        }
                        field = field(tenantId, draft.getObjectType(), rawFieldCode, memberId, expectedForm);
                        long count = draftRepository.findStagedFilesForUpdate(tenantId, draft.getId()).stream()
                                .filter(value -> value.field().getCode().sameValueAs(field.getCode())).count();
                        if (count >= 10) throw invalid("file field values exceed the supported limit");
                        return new Admission(tenantId, memberId, draft.getId(), draft.getObjectType(), field,
                                displayName, mediaType, expectedSha, request.getExpectedBytes(), storageReadiness, now);
                    }
                });
        if (!admission.isSuccess()) return copyFailure(admission);
        Admission subject = admission.getData();
        Instant deadline = subject.now.plus(runtimePolicy.intentTtl());
        AttachmentStorage.Allocation allocation;
        try { allocation = storage.allocate(subject.readiness.binding(), subject.expectedBytes, deadline); }
        catch (Exception exception) { return unavailable(); }
        Result<AttachmentUploadIntentVO> persisted = commandTemplate.execute(
                UseCaseOperation.ATTACHMENT_UPLOAD_INTENT_CREATE, new ServiceOperation<>() {
                    @Override public void validate() { }
                    @Override public AttachmentUploadIntentVO execute() {
                        objectLock.acquire(subject.tenantId, subject.objectType);
                        verifyDirectTarget(caller, subject.tenantId, directTarget,
                                AttachmentTargetOperation.CREATE_INTENT);
                        FileFieldDraft draft = draftRepository.findDraftForUpdate(subject.tenantId, subject.draftId)
                                .orElseThrow(() -> invalid("file field draft is unavailable"));
                        if (!draft.isActiveFor(subject.memberId, subject.objectType, local(clock.instant()))) {
                            throw invalid("file field draft is unavailable");
                        }
                        if (!java.util.Objects.equals(draft.getDirectTarget(), directTarget)) {
                            throw invalid("file field draft is unavailable");
                        }
                        verifyPreCreateDraftAuthorization(caller, subject.tenantId, subject.memberId, draft);
                        OaFormDefinition currentForm = null;
                        if (draft.getObjectType() == BusinessObjectType.OA_REQUEST) {
                            currentForm = formRepository.findByIdForUpdate(subject.tenantId, draft.getOaFormId())
                                    .filter(OaFormDefinition::isActive)
                                    .filter(value -> value.getVersion().equals(draft.getOaFormVersion()))
                                    .orElseThrow(() -> invalid("OA form is unavailable"));
                        }
                        FieldDefinition currentField = field(subject.tenantId, subject.objectType,
                                subject.field.getCode().getValue(), subject.memberId, currentForm);
                        if (!currentField.getId().sameValueAs(subject.field.getId())) {
                            throw invalid("file field is unavailable");
                        }
                        AttachmentFile file = AttachmentFile.createIntent(subject.tenantId, subject.memberId,
                                subject.displayName, subject.mediaType, subject.expectedBytes, subject.expectedSha,
                                local(subject.now));
                        fileRepository.saveStagedIntent(subject.tenantId, file);
                        if (!workStore.reserveUsage(subject.tenantId, new AttachmentWorkStore.UsageDelta(
                                0, 0, subject.expectedBytes, 1, 1, 0, 0),
                                runtimePolicy.usageLimits(), subject.now)) {
                            throw attachmentSupport.unavailable();
                        }
                        draftRepository.stage(subject.tenantId, subject.draftId, subject.field, file.getId(),
                                (int) draftRepository.findStagedFilesForUpdate(subject.tenantId, subject.draftId)
                                        .stream().filter(value -> value.field().getCode().sameValueAs(subject.field.getCode())).count());
                        workStore.createContentPlan(subject.tenantId, file.getId(), file.getContentVersion(), file.getVersion(),
                                subject.readiness.binding(), allocation.locator(), allocation.storageGeneration(), 1, deadline);
                        Instant jobDeadline = deadline.plus(runtimePolicy.tombstoneRetention());
                        workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.tenantId, file.getId(),
                                file.getContentVersion(), AttachmentWorkStore.JobKind.UPLOAD_RECONCILIATION,
                                1, deadline, jobDeadline));
                        workStore.createJobIfAbsent(new AttachmentWorkStore.JobRequest(subject.tenantId, file.getId(),
                                file.getContentVersion(), AttachmentWorkStore.JobKind.RETENTION_SWEEP,
                                1, deadline, jobDeadline));
                        return new AttachmentUploadIntentVO(file.getId().getValue(), file.getDisplayName(),
                                file.getDeclaredMediaType(), file.getExpectedBytes(), "UPLOADING", file.getVersion(),
                                local(deadline), directTarget == null ? null : subject.draftId.getValue());
                    }
                });
        if (!persisted.isSuccess()) {
            try {
                storage.abortIncomplete(subject.readiness.binding(), allocation.locator(),
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

    private FieldDefinition field(TenantId tenantId, BusinessObjectType objectType, String rawCode,
                                  MemberId memberId, OaFormDefinition expectedForm) {
        FieldCode code;
        try { code = new FieldCode(rawCode); } catch (RuntimeException exception) { throw invalid("file field code is invalid"); }
        FieldDefinition field = fieldRepository.findByCode(tenantId, objectType, code)
                .filter(FieldDefinition::isActive)
                .filter(value -> value.getFieldType() == CustomFieldType.FILE)
                .orElseThrow(() -> invalid("file field is unavailable"));
        if (objectType == BusinessObjectType.OA_REQUEST && expectedForm != null
                && expectedForm.getFields().stream().noneMatch(value -> value.fieldCode().equals(code))) {
            throw invalid("OA file field is unavailable");
        }
        var access = fieldAccessResolver.resolve(tenantId, memberId, objectType).get(code);
        if (access == null || !access.accessLevel().covers(FieldAccessLevel.WRITE)) {
            throw new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED, "file field is not writable");
        }
        return field;
    }

    private AttachmentTarget target(String value, Long id) {
        if (value == null || id == null || id <= 0) throw invalid("file field target is invalid");
        AttachmentTargetType type = switch (value.trim()) {
            case "customer" -> AttachmentTargetType.CUSTOMER; case "contact" -> AttachmentTargetType.CONTACT;
            case "lead" -> AttachmentTargetType.LEAD; case "opportunity" -> AttachmentTargetType.OPPORTUNITY;
            case "contract" -> AttachmentTargetType.CONTRACT; case "invoice" -> AttachmentTargetType.INVOICE;
            case "receivable" -> AttachmentTargetType.RECEIVABLE; case "receivable-plan" -> AttachmentTargetType.RECEIVABLE_PLAN;
            case "product" -> AttachmentTargetType.PRODUCT; case "return-visit" -> AttachmentTargetType.RETURN_VISIT;
            default -> throw invalid("file field target is unsupported");
        };
        return new AttachmentTarget(type, id);
    }
    private AttachmentTargetAccess targetAccess(AttachmentTarget target) {
        try { return targetAccessRegistry.require(target.type()); }
        catch (UnsupportedOperationException exception) { throw invalid("file field target is unsupported"); }
    }
    private void verifyDirectTarget(AuthenticatedCaller caller, TenantId tenantId,
                                    AttachmentTarget target, AttachmentTargetOperation operation) {
        if (target == null) return;
        var lifecycle = targetAccess(target).lockForAttachmentMutation(caller, target, operation);
        if (!tenantId.sameValueAs(lifecycle.tenantId()) || !lifecycle.attachmentsPermitted()) {
            throw attachmentSupport.notFound();
        }
    }
    private static BusinessObjectType crmObjectType(String raw) {
        try {
            BusinessObjectType type = BusinessObjectType.valueOf(raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT));
            if (type == BusinessObjectType.OA_REQUEST) {
                throw new IllegalArgumentException();
            }
            return type;
        } catch (RuntimeException exception) { throw invalid("file field draft object type is unsupported"); }
    }
    private static BusinessObjectType objectType(AttachmentTargetType type) {
        return switch (type) {
            case CUSTOMER -> BusinessObjectType.CUSTOMER; case CONTACT -> BusinessObjectType.CONTACT;
            case LEAD -> BusinessObjectType.LEAD; case OPPORTUNITY -> BusinessObjectType.OPPORTUNITY;
            case CONTRACT -> BusinessObjectType.CONTRACT; case INVOICE -> BusinessObjectType.INVOICE;
            case RECEIVABLE -> BusinessObjectType.RECEIVABLE; case RECEIVABLE_PLAN -> BusinessObjectType.RECEIVABLE_PLAN;
            case PRODUCT -> BusinessObjectType.PRODUCT; case RETURN_VISIT -> BusinessObjectType.RETURN_VISIT;
            case WORK_TASK, MARKETING_ACTIVITY, OA_ANNOUNCEMENT, OA_REQUEST, IMPORT_DRAFT ->
                    throw invalid("file field target is unsupported");
        };
    }
    private void verifyPreCreateDraftAuthorization(AuthenticatedCaller caller, TenantId tenantId,
                                                   MemberId memberId, FileFieldDraft draft) {
        if (draft.getDirectTarget() != null) {
            return;
        }
        preCreateDraftAuthorizer.requireCurrentWrite(caller, tenantId, memberId, draft.getObjectType());
    }
    private static FileFieldDraftId draftId(Long value) {
        try { return new FileFieldDraftId(value); } catch (RuntimeException exception) { throw invalid("file field draft ID is invalid"); }
    }
    private static FileFieldDraftVO vo(FileFieldDraft value) {
        return new FileFieldDraftVO(value.getId().getValue(), value.getObjectType().name(),
                value.getOaFormId() == null ? null : value.getOaFormId().getValue(), value.getOaFormVersion(),
                value.getVersion(), value.getDeadline());
    }
    private static LocalDateTime local(Instant instant) { return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }
    private static byte[] digest(TenantId tenant, MemberId member, BusinessObjectType type, AttachmentTarget target) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((tenant.getValue() + ":" + member.getValue() + ":" + type.name() + ":"
                    + (target == null ? "create" : target.type().name() + ":" + target.id()) + ":" + UUID.randomUUID())
                    .getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    private <T> Result<T> unavailable() {
        return commandTemplate.execute(UseCaseOperation.ATTACHMENT_UPLOAD_INTENT_CREATE, new ServiceOperation<>() {
            @Override public void validate() { throw attachmentSupport.unavailable(); }
            @Override public T execute() { throw new IllegalStateException(); }
        });
    }
    @SuppressWarnings("unchecked") private static <T> Result<T> copyFailure(Result<?> source) { return (Result<T>) source; }
    private static NonRetryableApplicationException invalid(String value) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, value);
    }
    private record Admission(TenantId tenantId, MemberId memberId, FileFieldDraftId draftId,
                             BusinessObjectType objectType, FieldDefinition field, String displayName,
                             String mediaType, byte[] expectedSha, long expectedBytes,
                             AttachmentStorageReadinessResolver.Snapshot readiness, Instant now) { }
}
