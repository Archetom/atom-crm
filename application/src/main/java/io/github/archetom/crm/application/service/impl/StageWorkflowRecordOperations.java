package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactQueryRequest;
import io.github.archetom.crm.api.dto.request.CustomerQueryRequest;
import io.github.archetom.crm.api.dto.request.LeadQueryRequest;
import io.github.archetom.crm.api.dto.request.StageAddressValueRequest;
import io.github.archetom.crm.api.dto.request.StageAdvanceRequest;
import io.github.archetom.crm.api.dto.request.StageCommentCreateRequest;
import io.github.archetom.crm.api.dto.request.StageOutcomeRequest;
import io.github.archetom.crm.application.assembler.StageWorkflowAssembler;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.ContactQueryService;
import io.github.archetom.crm.application.service.CustomerQueryService;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.FileFieldDraftService;
import io.github.archetom.crm.application.service.LeadQueryService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ApprovalSelectionCandidateVO;
import io.github.archetom.crm.application.vo.ApprovalStartSelectionVO;
import io.github.archetom.crm.application.vo.FileFieldDraftVO;
import io.github.archetom.crm.application.vo.StageAddressPartVO;
import io.github.archetom.crm.application.vo.StageAddressValueVO;
import io.github.archetom.crm.application.vo.StageApprovalGateVO;
import io.github.archetom.crm.application.vo.StageFieldCandidateVO;
import io.github.archetom.crm.application.vo.StageFieldInputVO;
import io.github.archetom.crm.application.vo.StageFieldOptionVO;
import io.github.archetom.crm.application.vo.StageOutcomeOptionVO;
import io.github.archetom.crm.application.vo.StageWorkflowCommentDeletionVO;
import io.github.archetom.crm.application.vo.StageWorkflowCommentVO;
import io.github.archetom.crm.application.vo.StageWorkflowRecordVO;
import io.github.archetom.crm.application.vo.StageWritableFieldVO;
import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.StageWorkflowComment;
import io.github.archetom.crm.domain.entity.StageWorkflowDefinition;
import io.github.archetom.crm.domain.entity.StageWorkflowRecord;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.ApprovalStepType;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FileFieldValue;
import io.github.archetom.crm.domain.model.StageDefinition;
import io.github.archetom.crm.domain.model.StageTaskDefinition;
import io.github.archetom.crm.domain.model.StageWorkflowRecordStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ApprovalDefinitionRepository;
import io.github.archetom.crm.domain.repository.ApprovalInstanceRepository;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.StageWorkflowCommentRepository;
import io.github.archetom.crm.domain.repository.StageWorkflowRecordRepository;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowCommentId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The stage-workflow record half: running a business record through a workflow, and its comments.
 *
 * <p>Starting a record, advancing it, recording an outcome and discussing it are one job with one
 * authority, {@code workflows:write}, and they are what {@code StageWorkflowRecordController}
 * exposes. Defining the workflows they run through is a different job with a different authority,
 * and now lives in {@link StageWorkflowDefinitionOperations}; the checks both need are in
 * {@link StageWorkflowSharedSupport}.</p>
 *
 * <p>Comments stay here rather than in a third class: they only exist against a record, they share
 * its visibility rules, and separating them would buy a class boundary without a concern behind
 * it.</p>
 */
@Component
@RequiredArgsConstructor
public class StageWorkflowRecordOperations {

    private final StageWorkflowRecordRepository recordRepository;
    private final StageWorkflowCommentRepository commentRepository;
    private final ApprovalDefinitionRepository approvalDefinitionRepository;
    private final ApprovalInstanceRepository approvalInstanceRepository;
    private final ApprovalSubmissionCoordinator submissionCoordinator;
    private final ApprovalApplicationSupport support;
    private final BusinessRecordFactResolver factResolver;
    private final WorkflowFieldAccessSupport fieldAccessSupport;
    private final OrganizationAccessGuard accessGuard;
    private final BusinessRecordAccessGuard recordAccessGuard;
    private final FileFieldRepository fileFieldRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final DepartmentRepository departmentRepository;
    private final CustomerQueryService customerQueryService;
    private final ContactQueryService contactQueryService;
    private final LeadQueryService leadQueryService;
    private final FileFieldBindingService fileFieldBindingService;
    private final FileFieldDraftService fileFieldDraftService;
    private final FileFieldSnapshotOutput fileFieldSnapshotOutput;
    private final CustomizationObjectLock objectLock;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;
    private final StageWorkflowSharedSupport shared;

    public Result<StageWorkflowRecordVO> start(
            AuthenticatedCaller caller, Long workflowId, Long subjectId) {
        return commandTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_RECORD_START,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private StageWorkflowId id;
                    private BusinessObjectType objectType;
                    private MemberId memberId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORKFLOWS_WRITE);
                        memberId = new MemberId(caller.actorId());
                        id = shared.workflowId(workflowId);
                        StageWorkflowDefinition candidate = shared.requireWorkflow(tenantId, id);
                        objectType = candidate.getObjectType();
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                objectType, subjectId, true));
                    }

                    @Override
                    public StageWorkflowRecordVO execute() {
                        objectLock.acquire(tenantId, objectType);
                        StageWorkflowDefinition workflow = shared.requireWorkflow(tenantId, id);
                        if (workflow.getObjectType() != objectType) {
                            throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Stage workflow object type changed unexpectedly");
                        }
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                workflow.getObjectType(), subjectId, true));
                        if (!workflow.isActive()) {
                            throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Stage workflow is not active");
                        }
                        shared.validateApprovalReferences(tenantId, workflow.getObjectType(),
                                workflow.getStages(), true);
                        OrganizationMembership membership = support.activeMembership(
                                tenantId, memberId);
                        if (!workflow.isApplicable(memberId, membership.getDepartmentId())) {
                            throw shared.error(ApplicationErrorCode.ACCESS_DENIED,
                                    "Caller is outside the stage workflow scope");
                        }
                        StageWorkflowRecord existing = recordRepository.findBySubject(
                                tenantId, workflow.getObjectType(), subjectId).orElse(null);
                        if (existing != null) {
                            if (!existing.getWorkflowId().equals(workflow.getId())) {
                                throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                        "Business record already has a stage workflow");
                            }
                            return toVO(caller, tenantId, memberId, existing, workflow);
                        }
                        StageWorkflowRecord record = StageWorkflowRecord.start(tenantId,
                                workflow.getId(), workflow.getVersion(), workflow.getObjectType(),
                                subjectId, workflow.firstStage());
                        StageWorkflowRecord saved = recordRepository.save(tenantId, record);
                        return toVO(caller, tenantId, memberId, saved, workflow);
                    }
                });
    }

    public Result<StageWorkflowRecordVO> getRecord(
            AuthenticatedCaller caller, Long workflowRecordId) {
        return queryTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_RECORD_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private StageWorkflowRecordId id;
                    private MemberId memberId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORKFLOWS_READ);
                        memberId = new MemberId(caller.actorId());
                        id = workflowRecordId(workflowRecordId);
                    }

                    @Override
                    public StageWorkflowRecordVO execute() {
                        StageWorkflowRecord record = requireRecord(tenantId, id, false);
                        recordAccessGuard.requireVisible(caller, record.getObjectType(),
                                record.getSubjectId(), false);
                        return toVO(caller, tenantId, memberId, record);
                    }
                });
    }

    public Result<Pager<ApprovalSelectionCandidateVO>> advanceSelectionCandidates(
            AuthenticatedCaller caller, Long workflowRecordId, String selectionCode,
            Integer page, Integer size) {
        return queryTemplate.execute(
                UseCaseOperation.STAGE_WORKFLOW_ADVANCE_SELECTION_CANDIDATE_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private StageWorkflowRecordId id;
                    private String code;
                    private int pageNumber;
                    private int pageSize;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORKFLOWS_WRITE);
                        memberId = new MemberId(caller.actorId());
                        id = workflowRecordId(workflowRecordId);
                        code = selectionCode == null ? null : selectionCode.trim();
                        pageNumber = shared.page(page);
                        pageSize = shared.size(size);
                        if (code == null || code.isEmpty() || pageNumber < 1 || pageSize < 1
                                || pageSize > 200) {
                            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Stage approval candidate query is invalid");
                        }
                        StageWorkflowRecord record = requireRecord(tenantId, id, false);
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                record.getObjectType(), record.getSubjectId(), true));
                    }

                    @Override
                    public Pager<ApprovalSelectionCandidateVO> execute() {
                        StageWorkflowRecord record = requireRecord(tenantId, id, false);
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                record.getObjectType(), record.getSubjectId(), true));
                        if (record.getStatus() != StageWorkflowRecordStatus.ACTIVE
                                || !canAdvance(caller, tenantId, record, true)) {
                            throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Stage approval selection is unavailable");
                        }
                        StageWorkflowDefinition workflow = shared.requireWorkflow(tenantId,
                                record.getWorkflowId());
                        StageDefinition stage = workflow.requireStage(record.getCurrentStageCode());
                        if (stage.approvalDefinitionId() == null) {
                            throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Current stage has no approval gate");
                        }
                        ApprovalDefinition definition = shared.requireApprovalDefinition(tenantId,
                                stage.approvalDefinitionId());
                        OrganizationMembership membership = support.activeMembership(
                                tenantId, memberId);
                        if (!definition.isActive() || !definition.isApplicantAllowed(memberId,
                                membership.getDepartmentId())) {
                            throw shared.error(ApplicationErrorCode.ACCESS_DENIED,
                                    "Caller is outside the stage approval initiator scope");
                        }
                        boolean selectionExists = definition.getSteps().stream()
                                .anyMatch(step -> step.type() == ApprovalStepType.APPLICANT_SELECT
                                        && step.code().equals(code));
                        if (!selectionExists) {
                            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Stage approval selection is unavailable");
                        }
                        PageResult<MemberProfile> profiles = memberProfileRepository.findActiveByTenant(
                                tenantId, null, pageNumber, pageSize);
                        Pager<ApprovalSelectionCandidateVO> result = new Pager<>();
                        result.setPageNum(profiles.page());
                        result.setPageSize(profiles.size());
                        result.setTotalNum(profiles.total());
                        result.setObjectList(profiles.items().stream().filter(MemberProfile::isActive)
                                .map(profile -> new ApprovalSelectionCandidateVO(
                                        profile.getId().getValue(), profile.getDisplayName())).toList());
                        return result;
                    }
                });
    }

    public Result<Pager<StageFieldCandidateVO>> fieldCandidates(
            AuthenticatedCaller caller, Long workflowRecordId, String fieldCode,
            String keyword, Integer page, Integer size) {
        return queryTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_FIELD_CANDIDATE_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private StageWorkflowRecordId id;
                    private FieldCode code;
                    private int pageNumber;
                    private int pageSize;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORKFLOWS_WRITE);
                        memberId = new MemberId(caller.actorId());
                        id = workflowRecordId(workflowRecordId);
                        try {
                            code = new FieldCode(fieldCode);
                        } catch (RuntimeException exception) {
                            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Stage field candidate query is invalid");
                        }
                        pageNumber = shared.page(page);
                        pageSize = shared.size(size);
                        if (pageNumber < 1 || pageSize < 1 || pageSize > 200) {
                            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Stage field candidate page is invalid");
                        }
                        StageWorkflowRecord record = requireRecord(tenantId, id, false);
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                record.getObjectType(), record.getSubjectId(), true));
                    }

                    @Override
                    public Pager<StageFieldCandidateVO> execute() {
                        StageWorkflowRecord record = requireRecord(tenantId, id, false);
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                record.getObjectType(), record.getSubjectId(), true));
                        if (record.getStatus() != StageWorkflowRecordStatus.ACTIVE
                                || !canAdvance(caller, tenantId, record, true)) {
                            throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Stage field candidates are unavailable");
                        }
                        StageWorkflowDefinition workflow = shared.requireWorkflow(tenantId,
                                record.getWorkflowId());
                        StageDefinition stage = workflow.requireStage(record.getCurrentStageCode());
                        if (!stage.requiredFieldCodes().contains(code)) {
                            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Stage field is not writable for the current stage");
                        }
                        Map<FieldCode, FieldDefinition> fields = support.fields(tenantId,
                                record.getObjectType());
                        FieldDefinition field = fields.get(code);
                        if (field == null) {
                            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Stage field is unavailable");
                        }
                        var access = fieldAccessSupport.accesses(tenantId, memberId,
                                record.getObjectType()).get(code);
                        if (access == null || access.accessLevel() != FieldAccessLevel.WRITE) {
                            throw shared.error(ApplicationErrorCode.ACCESS_DENIED,
                                    "Stage field is not writable");
                        }
                        return switch (field.getFieldType()) {
                            case USER -> shared.actorCandidates(tenantId, keyword, pageNumber, pageSize);
                            case DEPARTMENT -> shared.departmentCandidates(tenantId, keyword, pageNumber, pageSize);
                            case RELATION -> relationCandidates(caller, record.getObjectType(), code,
                                    keyword, pageNumber, pageSize);
                            default -> throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Stage field does not use candidates");
                        };
                    }
                });
    }

    public Result<FileFieldDraftVO> createFileDraft(
            AuthenticatedCaller caller, Long workflowRecordId, String fieldCode) {
        return queryTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_FILE_DRAFT_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private StageWorkflowRecordId id;
                    private FieldCode code;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(caller,
                                AuthorityCatalog.WORKFLOWS_WRITE);
                        memberId = new MemberId(caller.actorId());
                        id = workflowRecordId(workflowRecordId);
                        code = shared.stageFieldCode(fieldCode);
                        StageWorkflowRecord record = requireRecord(tenantId, id, false);
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                record.getObjectType(), record.getSubjectId(), true));
                    }

                    @Override
                    public FileFieldDraftVO execute() {
                        StageWorkflowRecord record = requireRecord(tenantId, id, false);
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                record.getObjectType(), record.getSubjectId(), true));
                        if (record.getStatus() != StageWorkflowRecordStatus.ACTIVE
                                || !canAdvance(caller, tenantId, record, true)) {
                            throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Stage file draft is unavailable");
                        }
                        StageWorkflowDefinition workflow = shared.requireWorkflow(tenantId,
                                record.getWorkflowId());
                        StageDefinition stage = workflow.requireStage(record.getCurrentStageCode());
                        FieldDefinition field = support.fields(tenantId, record.getObjectType()).get(code);
                        var access = field == null ? null : fieldAccessSupport.accesses(tenantId,
                                memberId, record.getObjectType()).get(code);
                        if (!stage.requiredFieldCodes().contains(code) || field == null
                                || field.getFieldType() != CustomFieldType.FILE || access == null
                                || access.accessLevel() != FieldAccessLevel.WRITE) {
                            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Stage file field is not writable for the current stage");
                        }
                        Result<FileFieldDraftVO> draft = fileFieldDraftService.createStageWorkflowDraft(
                                caller, record.getId().getValue(), record.getObjectType().name());
                        if (!draft.isSuccess() || draft.getData() == null) {
                            throw shared.error(ApplicationErrorCode.ACCESS_DENIED,
                                    "Stage file draft is unavailable");
                        }
                        return draft.getData();
                    }
                });
    }

    public Result<StageWorkflowRecordVO> advance(
            AuthenticatedCaller caller, Long workflowRecordId,
            StageAdvanceRequest request) {
        return commandTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_RECORD_ADVANCE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private StageWorkflowRecordId id;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORKFLOWS_WRITE);
                        memberId = new MemberId(caller.actorId());
                        id = workflowRecordId(workflowRecordId);
                        shared.require(request, "Stage advance request");
                        StageWorkflowRecord record = requireRecord(tenantId, id, false);
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                record.getObjectType(), record.getSubjectId(), true));
                    }

                    @Override
                    public StageWorkflowRecordVO execute() {
                        StageWorkflowRecord snapshot = requireRecord(tenantId, id, false);
                        objectLock.acquire(tenantId, snapshot.getObjectType());
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                snapshot.getObjectType(), snapshot.getSubjectId(), true));
                        StageWorkflowRecord record = requireRecord(tenantId, id, true);
                        shared.requireVersion(record.getVersion(), request.getVersion(),
                                "Stage workflow record version is stale");
                        StageWorkflowDefinition workflow = shared.requireWorkflow(
                                tenantId, record.getWorkflowId());
                        StageDefinition current = workflow.requireStage(
                                record.getCurrentStageCode());
                        if (!canAdvance(caller, tenantId, record, canWriteRecord(caller, tenantId, record))) {
                            throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Stage workflow advance is not allowed");
                        }
                        if (record.getStatus() == StageWorkflowRecordStatus.AWAITING_APPROVAL) {
                            ApprovalInstance approval = requireApproval(tenantId,
                                    record.getPendingApprovalInstanceId());
                            if (approval.getStatus() == ApprovalInstanceStatus.PENDING) {
                                throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                        "Stage approval is still pending");
                            }
                            if (!record.resolveApproval(approval.getStatus())) {
                                return toVO(caller, tenantId, memberId,
                                        recordRepository.save(tenantId, record));
                            }
                            record.completeCurrent(current,
                                    workflow.nextStage(current.code()));
                            return toVO(caller, tenantId, memberId,
                                    recordRepository.save(tenantId, record));
                        }
                        Map<FieldCode, FieldDefinition> fields = support.fields(
                                tenantId, workflow.getObjectType());
                        Map<FieldCode, List<String>> scalarValues = support.normalizeFacts(
                                request.getFieldValues(), fields);
                        Set<FieldCode> allowedScalarFields = current.requiredFieldCodes().stream()
                                .filter(code -> fields.get(code) != null
                                        && scalarStageInput(fields.get(code).getFieldType()))
                                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                        if (!allowedScalarFields.containsAll(scalarValues.keySet())) {
                            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Stage field is not writable for the current stage");
                        }
                        Set<String> allowedTasks = current.tasks().stream()
                                .map(StageTaskDefinition::code)
                                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                        Set<String> requestedTasks = request.getCompletedTaskCodes() == null
                                ? Set.of() : request.getCompletedTaskCodes();
                        if (!allowedTasks.containsAll(requestedTasks)) {
                            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Stage task is not available for the current stage");
                        }
                        Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> accesses =
                                fieldAccessSupport.accesses(tenantId, memberId, workflow.getObjectType());
                        Map<FieldCode, List<String>> values = new LinkedHashMap<>(scalarValues);
                        mergeSpecialValues(values, typedStageValues(caller, tenantId, memberId, record,
                                current, fields, request));
                        fieldAccessSupport.requireWrite(values.keySet(), accesses);
                        Set<FieldCode> requiredFiles = current.requiredFieldCodes().stream()
                                .filter(code -> fields.get(code) != null
                                        && fields.get(code).getFieldType() == CustomFieldType.FILE)
                                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                        fieldAccessSupport.requireWrite(requiredFiles, accesses);
                        if (request.getFileFieldDraftId() != null
                                || request.getFileFields() != null && !request.getFileFields().isEmpty()) {
                            if (!requiredFiles.containsAll(fileFieldCodes(request.getFileFields()))) {
                                throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                        "Stage file field is not writable for the current stage");
                            }
                            fileFieldBindingService.consumeStageWorkflowDraft(tenantId, memberId,
                                    workflow.getObjectType(), record.getId(), request.getFileFieldDraftId(),
                                    attachmentTarget(record), request.getFileFields(),
                                    requiredFileDefinitions(fields, requiredFiles), accesses);
                        }
                        Map<FieldCode, List<FileFieldValue>> fileValues = currentFileValues(tenantId, record);
                        if (requiredFiles.stream().anyMatch(code -> fileValues.getOrDefault(code, List.of()).isEmpty())) {
                            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "A required stage file field is missing");
                        }
                        record.capture(current, values, requiredFiles, request.getCompletedTaskCodes());
                        fileFieldRepository.snapshotStageWorkflowRecord(tenantId, record.getId(),
                                record.getObjectType(), current.code(), fileValues);
                        if (current.approvalDefinitionId() == null) {
                            record.completeCurrent(current, workflow.nextStage(current.code()));
                        } else {
                            ApprovalDefinition definition = shared.requireApprovalDefinition(
                                    tenantId, current.approvalDefinitionId());
                            String key = submissionCoordinator.stageIdempotencyKey(
                                    record.getId(), current.code(), record.nextApprovalAttempt());
                            Map<FieldCode, List<String>> approvalFacts = approvalFacts(
                                    tenantId, definition, record.getSubjectId(), values, fields);
                            ApprovalInstance approval = submissionCoordinator.submit(tenantId,
                                    definition, record.getSubjectId(), key,
                                    stageApprovalTitle(workflow.getName(), current.name()), memberId,
                                    () -> approvalFacts, request.getSelectedApprovers(), List.of());
                            fileFieldRepository.snapshotApprovalInstance(tenantId, approval.getId(),
                                    record.getObjectType(), fileValues);
                            if (approval.getStatus() == ApprovalInstanceStatus.APPROVED) {
                                record.completeCurrent(current,
                                        workflow.nextStage(current.code()));
                            } else {
                                record.awaitApproval(approval.getId());
                            }
                        }
                        return toVO(caller, tenantId, memberId,
                                recordRepository.save(tenantId, record));
                    }
                });
    }

    public Result<StageWorkflowRecordVO> outcome(
            AuthenticatedCaller caller, Long workflowRecordId,
            StageOutcomeRequest request) {
        return commandTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_RECORD_OUTCOME,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private StageWorkflowRecordId id;
                    private MemberId memberId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORKFLOWS_WRITE);
                        memberId = new MemberId(caller.actorId());
                        id = workflowRecordId(workflowRecordId);
                        shared.require(request, "Stage outcome request");
                        if (!"FAILED".equalsIgnoreCase(request.getOutcome())) {
                            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Only the failed outcome can be set explicitly");
                        }
                        StageWorkflowRecord record = requireRecord(tenantId, id, false);
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                record.getObjectType(), record.getSubjectId(), true));
                    }

                    @Override
                    public StageWorkflowRecordVO execute() {
                        StageWorkflowRecord snapshot = requireRecord(tenantId, id, false);
                        objectLock.acquire(tenantId, snapshot.getObjectType());
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                snapshot.getObjectType(), snapshot.getSubjectId(), true));
                        StageWorkflowRecord record = requireRecord(tenantId, id, true);
                        shared.requireVersion(record.getVersion(), request.getVersion(),
                                "Stage workflow record version is stale");
                        if (!canWriteRecord(caller, tenantId, record)
                                || record.getStatus() == StageWorkflowRecordStatus.SUCCEEDED
                                || record.getStatus() == StageWorkflowRecordStatus.FAILED) {
                            throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Stage workflow outcome is not allowed");
                        }
                        if (record.getPendingApprovalInstanceId() != null) {
                            ApprovalInstance approval = approvalInstanceRepository
                                    .findByIdForUpdate(tenantId,
                                            record.getPendingApprovalInstanceId())
                                    .orElseThrow(() -> shared.error(
                                            ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                            "Stage approval instance was not found"));
                            if (approval.getStatus() == ApprovalInstanceStatus.PENDING) {
                                approval.cancel();
                                approvalInstanceRepository.save(tenantId, approval);
                            }
                        }
                        record.fail(request.getRemark());
                        return toVO(caller, tenantId, memberId,
                                recordRepository.save(tenantId, record));
                    }
                });
    }

    public Result<Pager<StageWorkflowCommentVO>> comments(
            AuthenticatedCaller caller, Long workflowRecordId, Integer page, Integer size) {
        return queryTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_COMMENT_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private StageWorkflowRecordId id;
                    private MemberId memberId;
                    private int pageNumber;
                    private int pageSize;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORKFLOWS_READ);
                        memberId = new MemberId(caller.actorId());
                        id = workflowRecordId(workflowRecordId);
                        pageNumber = shared.page(page);
                        pageSize = shared.size(size);
                        if (pageNumber < 1 || pageSize < 1 || pageSize > 200) {
                            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Stage comment page is invalid");
                        }
                        StageWorkflowRecord record = requireRecord(tenantId, id, false);
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                record.getObjectType(), record.getSubjectId(), false));
                    }

                    @Override
                    public Pager<StageWorkflowCommentVO> execute() {
                        return shared.pager(commentRepository.findPage(
                                tenantId, id, pageNumber, pageSize),
                                comment -> StageWorkflowAssembler.toVO(comment, memberId));
                    }
                });
    }

    public Result<StageWorkflowCommentVO> comment(
            AuthenticatedCaller caller, Long workflowRecordId,
            StageCommentCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_COMMENT_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private StageWorkflowRecordId id;
                    private MemberId memberId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORKFLOWS_WRITE);
                        id = workflowRecordId(workflowRecordId);
                        memberId = new MemberId(caller.actorId());
                        shared.require(request, "Stage comment request");
                        StageWorkflowRecord record = requireRecord(tenantId, id, false);
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                record.getObjectType(), record.getSubjectId(), true));
                    }

                    @Override
                    public StageWorkflowCommentVO execute() {
                        StageWorkflowRecord record = requireRecord(tenantId, id, false);
                        objectLock.acquire(tenantId, record.getObjectType());
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                record.getObjectType(), record.getSubjectId(), true));
                        return StageWorkflowAssembler.toVO(commentRepository.save(tenantId,
                                StageWorkflowComment.create(tenantId, id, memberId,
                                        request.getContent())), memberId);
                    }
                });
    }

    public Result<StageWorkflowCommentDeletionVO> deleteComment(
            AuthenticatedCaller caller, Long workflowRecordId,
            Long commentId, Long version) {
        return commandTemplate.execute(UseCaseOperation.STAGE_WORKFLOW_COMMENT_DELETE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private StageWorkflowRecordId recordId;
                    private StageWorkflowCommentId id;
                    private MemberId memberId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.WORKFLOWS_WRITE);
                        recordId = workflowRecordId(workflowRecordId);
                        id = commentId(commentId);
                        memberId = new MemberId(caller.actorId());
                        StageWorkflowRecord record = requireRecord(tenantId, recordId, false);
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                record.getObjectType(), record.getSubjectId(), true));
                    }

                    @Override
                    public StageWorkflowCommentDeletionVO execute() {
                        StageWorkflowRecord record = requireRecord(tenantId, recordId, false);
                        objectLock.acquire(tenantId, record.getObjectType());
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                                record.getObjectType(), record.getSubjectId(), true));
                        StageWorkflowComment comment = commentRepository.findById(
                                        tenantId, recordId, id)
                                .orElseThrow(() -> shared.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                        "Stage comment was not found"));
                        shared.requireVersion(comment.getVersion(), version,
                                "Stage comment version is stale");
                        comment.delete(memberId);
                        commentRepository.save(tenantId, comment);
                        return new StageWorkflowCommentDeletionVO(comment.getId().getValue(), true);
                    }
                });
    }

    private Pager<StageFieldCandidateVO> relationCandidates(
            AuthenticatedCaller caller, BusinessObjectType sourceType, FieldCode fieldCode,
            String keyword, int page, int size) {
        BusinessObjectType target = shared.relationTarget(sourceType, fieldCode);
        if (target == null) {
            throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Stage relation field has no supported target catalog");
        }
        return switch (target) {
            case CUSTOMER -> customerCandidates(caller, keyword, page, size);
            case CONTACT -> contactCandidates(caller, keyword, page, size);
            case LEAD -> leadCandidates(caller, keyword, page, size);
            default -> throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Stage relation target is unavailable");
        };
    }

    private Pager<StageFieldCandidateVO> customerCandidates(
            AuthenticatedCaller caller, String keyword, int page, int size) {
        CustomerQueryRequest request = new CustomerQueryRequest();
        request.setKeyword(shared.blankToNull(keyword)); request.setPage(page); request.setSize(size);
        Result<Pager<io.github.archetom.crm.application.vo.CustomerVO>> result =
                customerQueryService.query(caller, request);
        if (!result.isSuccess() || result.getData() == null) {
            throw shared.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Relation candidates are not visible to the caller");
        }
        Pager<io.github.archetom.crm.application.vo.CustomerVO> values = result.getData();
        return shared.candidatePager(values.getPageNum(), values.getPageSize(), values.getTotalNum(),
                values.getObjectList().stream().map(value -> new StageFieldCandidateVO(value.id(),
                        value.name(), "RELATION_ID")).toList());
    }

    private Pager<StageFieldCandidateVO> contactCandidates(
            AuthenticatedCaller caller, String keyword, int page, int size) {
        ContactQueryRequest request = new ContactQueryRequest();
        request.setKeyword(shared.blankToNull(keyword)); request.setPage(page); request.setSize(size);
        Result<Pager<io.github.archetom.crm.application.vo.ContactVO>> result =
                contactQueryService.query(caller, request);
        if (!result.isSuccess() || result.getData() == null) {
            throw shared.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Relation candidates are not visible to the caller");
        }
        Pager<io.github.archetom.crm.application.vo.ContactVO> values = result.getData();
        return shared.candidatePager(values.getPageNum(), values.getPageSize(), values.getTotalNum(),
                values.getObjectList().stream().map(value -> new StageFieldCandidateVO(value.id(),
                        value.name(), "RELATION_ID")).toList());
    }

    private Pager<StageFieldCandidateVO> leadCandidates(
            AuthenticatedCaller caller, String keyword, int page, int size) {
        LeadQueryRequest request = new LeadQueryRequest();
        request.setKeyword(shared.blankToNull(keyword)); request.setPage(page); request.setSize(size);
        Result<Pager<io.github.archetom.crm.application.vo.LeadVO>> result =
                leadQueryService.query(caller, request);
        if (!result.isSuccess() || result.getData() == null) {
            throw shared.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Relation candidates are not visible to the caller");
        }
        Pager<io.github.archetom.crm.application.vo.LeadVO> values = result.getData();
        return shared.candidatePager(values.getPageNum(), values.getPageSize(), values.getTotalNum(),
                values.getObjectList().stream().map(value -> new StageFieldCandidateVO(value.id(),
                        value.name(), "RELATION_ID")).toList());
    }

    private StageWorkflowRecord requireRecord(
            TenantId tenantId, StageWorkflowRecordId id, boolean lock) {
        return (lock ? recordRepository.findByIdForUpdate(tenantId, id)
                : recordRepository.findById(tenantId, id)).orElseThrow(() ->
                shared.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Stage workflow record was not found"));
    }

    private ApprovalInstance requireApproval(TenantId tenantId, ApprovalInstanceId id) {
        return approvalInstanceRepository.findById(tenantId, id).orElseThrow(() ->
                shared.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Stage approval instance was not found"));
    }

    private StageWorkflowRecordId workflowRecordId(Long value) {
        try {
            return new StageWorkflowRecordId(value);
        } catch (RuntimeException exception) {
            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Stage workflow record ID is invalid");
        }
    }

    private StageWorkflowCommentId commentId(Long value) {
        try {
            return new StageWorkflowCommentId(value);
        } catch (RuntimeException exception) {
            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Stage comment ID is invalid");
        }
    }

    private boolean scalarStageInput(CustomFieldType type) {
        return type != null && switch (type) {
            case FILE, USER, DEPARTMENT, RELATION, ADDRESS -> false;
            default -> true;
        };
    }

    private Map<FieldCode, List<String>> typedStageValues(
            AuthenticatedCaller caller, TenantId tenantId, MemberId memberId,
            StageWorkflowRecord record, StageDefinition stage,
            Map<FieldCode, FieldDefinition> fields, StageAdvanceRequest request) {
        LinkedHashMap<String, List<String>> raw = new LinkedHashMap<>();
        Set<FieldCode> allowed = stage.requiredFieldCodes().stream()
                .filter(code -> fields.get(code) != null)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        mergeTypedIdentifiers(raw, request.getActorFieldValues(), CustomFieldType.USER, allowed,
                fields, "actor");
        mergeTypedIdentifiers(raw, request.getDepartmentFieldValues(), CustomFieldType.DEPARTMENT,
                allowed, fields, "department");
        mergeTypedIdentifiers(raw, request.getRelationFieldValues(), CustomFieldType.RELATION,
                allowed, fields, "relation");
        mergeTypedAddresses(raw, request.getAddressFieldValues(), allowed, fields);
        Map<FieldCode, List<String>> normalized = support.normalizeFacts(raw, fields);
        validateTypedIdentifiers(caller, tenantId, memberId, record, fields, normalized,
                CustomFieldType.USER);
        validateTypedIdentifiers(caller, tenantId, memberId, record, fields, normalized,
                CustomFieldType.DEPARTMENT);
        validateTypedIdentifiers(caller, tenantId, memberId, record, fields, normalized,
                CustomFieldType.RELATION);
        return normalized;
    }

    private void mergeTypedIdentifiers(Map<String, List<String>> target,
                                       Map<String, List<Long>> source, CustomFieldType expected,
                                       Set<FieldCode> allowed,
                                       Map<FieldCode, FieldDefinition> fields, String description) {
        if (source == null) {
            return;
        }
        for (Map.Entry<String, List<Long>> entry : source.entrySet()) {
            FieldCode code = shared.stageFieldCode(entry.getKey());
            FieldDefinition definition = fields.get(code);
            if (!allowed.contains(code) || definition == null || definition.getFieldType() != expected) {
                throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Stage " + description + " field is not writable for the current stage");
            }
            List<Long> ids = entry.getValue() == null ? List.of() : entry.getValue();
            if (ids.isEmpty() || ids.size() > 20 || ids.stream().anyMatch(value -> value == null || value <= 0)) {
                throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Stage " + description + " values are invalid");
            }
            List<String> values = ids.stream().map(String::valueOf).distinct().toList();
            if (target.putIfAbsent(code.getValue(), values) != null) {
                throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Stage field value is duplicated");
            }
        }
    }

    private void mergeTypedAddresses(Map<String, List<String>> target,
                                     Map<String, StageAddressValueRequest> source,
                                     Set<FieldCode> allowed,
                                     Map<FieldCode, FieldDefinition> fields) {
        if (source == null) {
            return;
        }
        for (Map.Entry<String, StageAddressValueRequest> entry : source.entrySet()) {
            FieldCode code = shared.stageFieldCode(entry.getKey());
            FieldDefinition definition = fields.get(code);
            if (!allowed.contains(code) || definition == null
                    || definition.getFieldType() != CustomFieldType.ADDRESS || entry.getValue() == null) {
                throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Stage address field is not writable for the current stage");
            }
            if (target.putIfAbsent(code.getValue(), List.of(stageAddress(entry.getValue()))) != null) {
                throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Stage field value is duplicated");
            }
        }
    }

    private void validateTypedIdentifiers(AuthenticatedCaller caller, TenantId tenantId,
                                          MemberId memberId, StageWorkflowRecord record,
                                          Map<FieldCode, FieldDefinition> fields,
                                          Map<FieldCode, List<String>> values,
                                          CustomFieldType type) {
        values.forEach((code, ids) -> {
            FieldDefinition field = fields.get(code);
            if (field == null || field.getFieldType() != type) {
                return;
            }
            for (String value : ids) {
                long rawId;
                try {
                    rawId = Long.parseLong(value);
                } catch (RuntimeException exception) {
                    throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Stage typed identifier is invalid");
                }
                switch (type) {
                    case USER -> {
                        MemberId candidate = new MemberId(rawId);
                        MemberProfile profile = memberProfileRepository.findById(candidate)
                                .filter(MemberProfile::isActive).orElseThrow(() ->
                                        shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                                                "Stage actor candidate is unavailable"));
                        support.activeMembership(tenantId, profile.getId());
                    }
                    case DEPARTMENT -> departmentRepository.findById(tenantId, new DepartmentId(rawId))
                            .filter(Department::isActive).orElseThrow(() -> shared.error(
                                    ApplicationErrorCode.PARAMETER_INVALID,
                                    "Stage department candidate is unavailable"));
                    case RELATION -> {
                        BusinessObjectType target = shared.relationTarget(record.getObjectType(), code);
                        if (target == null) {
                            throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Stage relation field has no supported target catalog");
                        }
                        shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(
                                caller, target, rawId, false));
                    }
                    default -> throw new IllegalStateException("Unexpected stage field type");
                }
            }
        });
    }

    private void mergeSpecialValues(Map<FieldCode, List<String>> target,
                                    Map<FieldCode, List<String>> additions) {
        for (Map.Entry<FieldCode, List<String>> entry : additions.entrySet()) {
            if (target.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Stage field value must use exactly one input contract");
            }
        }
    }

    private Set<FieldCode> fileFieldCodes(Map<String, List<Long>> source) {
        if (source == null) {
            return Set.of();
        }
        return source.keySet().stream().map(shared::stageFieldCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<FieldCode, FieldDefinition> requiredFileDefinitions(
            Map<FieldCode, FieldDefinition> fields, Set<FieldCode> codes) {
        LinkedHashMap<FieldCode, FieldDefinition> result = new LinkedHashMap<>();
        for (FieldCode code : codes) {
            FieldDefinition field = fields.get(code);
            if (field != null) {
                result.put(code, field);
            }
        }
        return Map.copyOf(result);
    }

    private AttachmentTarget attachmentTarget(StageWorkflowRecord record) {
        try {
            return new AttachmentTarget(AttachmentTargetType.valueOf(record.getObjectType().name()),
                    record.getSubjectId());
        } catch (RuntimeException exception) {
            throw shared.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Stage record does not support file fields");
        }
    }

    private String stageAddress(StageAddressValueRequest request) {
        if (request.getAddressLine() == null || request.getAddressLine().isBlank()) {
            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID, "Stage address line is required");
        }
        List<String> components = List.of(request.getCountry(), request.getRegion(), request.getCity(),
                request.getPostalCode(), request.getAddressLine().trim(),
                request.getLongitude() == null ? null : request.getLongitude().stripTrailingZeros().toPlainString(),
                request.getLatitude() == null ? null : request.getLatitude().stripTrailingZeros().toPlainString());
        String value = "v1." + components.stream().map(component -> component == null ? ""
                : Base64.getUrlEncoder().withoutPadding().encodeToString(
                        component.trim().getBytes(StandardCharsets.UTF_8)))
                .collect(java.util.stream.Collectors.joining("."));
        if (value.length() > 500) {
            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID, "Stage address is too long");
        }
        return value;
    }

    private StageAddressValueVO currentAddress(List<String> values) {
        if (values == null || values.isEmpty() || values.getFirst() == null
                || values.getFirst().isBlank()) {
            return null;
        }
        String value = values.getFirst();
        if (!value.startsWith("v1.")) {
            return new StageAddressValueVO(null, null, null, null, value, null, null);
        }
        String[] parts = value.substring(3).split("\\.", -1);
        if (parts.length != 7) {
            return null;
        }
        try {
            return new StageAddressValueVO(addressComponent(parts[0]), addressComponent(parts[1]),
                    addressComponent(parts[2]), addressComponent(parts[3]),
                    addressComponent(parts[4]), decimalComponent(parts[5]),
                    decimalComponent(parts[6]));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String addressComponent(String value) {
        return value == null || value.isBlank() ? null
                : new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private BigDecimal decimalComponent(String value) {
        String decoded = addressComponent(value);
        return decoded == null ? null : new BigDecimal(decoded);
    }

    private Map<FieldCode, List<FileFieldValue>> currentFileValues(TenantId tenantId,
                                                                     StageWorkflowRecord record) {
        AttachmentTarget target;
        try {
            target = new AttachmentTarget(AttachmentTargetType.valueOf(record.getObjectType().name()),
                    record.getSubjectId());
        } catch (RuntimeException exception) {
            return Map.of();
        }
        Map<FieldCode, List<FileFieldValue>> values = new java.util.LinkedHashMap<>();
        fileFieldRepository.findCrmValues(tenantId, target).forEach((field, bound) -> values.put(field,
                bound.stream().map(value -> new FileFieldValue(field, value.file().getId(), value.ordinal(),
                        value.contentVersion(), value.evidenceDigest())).toList()));
        return Map.copyOf(values);
    }

    private StageWorkflowRecordVO toVO(
            AuthenticatedCaller caller, TenantId tenantId, MemberId memberId,
            StageWorkflowRecord record, StageWorkflowDefinition workflow) {
        Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> accesses =
                fieldAccessSupport.accesses(tenantId, memberId, record.getObjectType());
        StageDefinition current = record.getCurrentStageCode() == null ? null
                : workflow.requireStage(record.getCurrentStageCode());
        boolean canWrite = canWriteRecord(caller, tenantId, record);
        boolean canAdvance = canAdvance(caller, tenantId, record, canWrite);
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        if (canAdvance) {
            actions.add("ADVANCE");
        }
        if (canWrite && record.getStatus() != StageWorkflowRecordStatus.SUCCEEDED
                && record.getStatus() != StageWorkflowRecordStatus.FAILED) {
            actions.add("FAIL");
        }
        if (canWrite) {
            actions.add("COMMENT");
        }
        List<String> writable = canAdvance && record.getStatus() == StageWorkflowRecordStatus.ACTIVE
                && current != null ? current.requiredFieldCodes().stream()
                .filter(code -> accesses.get(code) != null
                        && accesses.get(code).accessLevel() == FieldAccessLevel.WRITE)
                .map(FieldCode::getValue).toList() : List.of();
        List<StageWritableFieldVO> writableFields = writableFields(tenantId, record, current,
                accesses, writable);
        List<String> taskCodes = canAdvance && record.getStatus() == StageWorkflowRecordStatus.ACTIVE
                && current != null ? current.tasks().stream().map(StageTaskDefinition::code).toList()
                : List.of();
        return StageWorkflowAssembler.toVO(record, accesses,
                fileFieldSnapshotOutput.visibleByStage(fileFieldRepository.findStageWorkflowValues(
                        tenantId, record.getId()), accesses,
                        caller != null && caller.hasAuthority(AuthorityCatalog.FILES_READ)),
                current, actions, allowedOutcomes(workflow, record, canWrite), writable, writableFields, taskCodes,
                approvalGate(tenantId, record, current));
    }

    private List<StageWritableFieldVO> writableFields(
            TenantId tenantId, StageWorkflowRecord record, StageDefinition current,
            Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> accesses,
            List<String> writableCodes) {
        if (current == null || writableCodes == null || writableCodes.isEmpty()) {
            return List.of();
        }
        Map<FieldCode, FieldDefinition> definitions = support.fields(tenantId,
                record.getObjectType());
        Set<FieldCode> codes = current.requiredFieldCodes().stream()
                .filter(code -> writableCodes.contains(code.getValue()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<FieldCode, List<String>> currentValues = currentFieldValues(tenantId, record,
                definitions, codes);
        return codes.stream().map(code -> {
            FieldDefinition definition = definitions.get(code);
            if (definition == null) {
                throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Current stage field definition is unavailable");
            }
            var access = accesses.get(code);
            boolean masked = access == null || access.maskType()
                    != io.github.archetom.crm.domain.model.FieldMaskType.NONE;
            List<String> values = masked ? List.of() : currentValues.getOrDefault(code, List.of());
            StageAddressValueVO address = !masked
                    && definition.getFieldType() == CustomFieldType.ADDRESS
                    ? currentAddress(values) : null;
            return new StageWritableFieldVO(code.getValue(), definition.getDisplayName(),
                    definition.getFieldType().name(), true, definition.getOptions().stream()
                    .map(option -> new StageFieldOptionVO(option.value(), option.label())).toList(),
                    definition.getFieldType() == CustomFieldType.ADDRESS ? List.of() : values, address,
                    fieldInput(record.getObjectType(), code, definition.getFieldType()), masked);
        }).toList();
    }

    private List<StageOutcomeOptionVO> allowedOutcomes(
            StageWorkflowDefinition workflow, StageWorkflowRecord record, boolean canWrite) {
        if (!canWrite || record.getStatus() == StageWorkflowRecordStatus.SUCCEEDED
                || record.getStatus() == StageWorkflowRecordStatus.FAILED) {
            return List.of();
        }
        return List.of(new StageOutcomeOptionVO("FAILED", workflow.getFailureName(), false, 1000));
    }

    private StageFieldInputVO fieldInput(
            BusinessObjectType objectType, FieldCode code, CustomFieldType type) {
        return switch (type) {
            case SELECT, MULTI_SELECT -> new StageFieldInputVO("OPTION_VALUES", false, null,
                    false, List.of(), true, null);
            case USER -> new StageFieldInputVO("ACTOR_IDS", true, null,
                    false, List.of(), true, null);
            case DEPARTMENT -> new StageFieldInputVO("DEPARTMENT_IDS", true, null,
                    false, List.of(), true, null);
            case RELATION -> {
                BusinessObjectType target = shared.relationTarget(objectType, code);
                yield target == null
                        ? new StageFieldInputVO("UNSUPPORTED", true, null, false, List.of(),
                        false, "RELATION_TARGET_UNDECLARED")
                        : new StageFieldInputVO("RELATION_IDS", true, target.name(), false,
                        List.of(), true, null);
            }
            case ADDRESS -> new StageFieldInputVO("ADDRESS", false, null, false,
                    List.of(new StageAddressPartVO("country", "Country", false, 100),
                            new StageAddressPartVO("region", "Region", false, 100),
                            new StageAddressPartVO("city", "City", false, 100),
                            new StageAddressPartVO("postalCode", "Postal code", false, 30),
                            new StageAddressPartVO("addressLine", "Address line", true, 500),
                            new StageAddressPartVO("longitude", "Longitude", false, 30),
                            new StageAddressPartVO("latitude", "Latitude", false, 30)), true, null);
            case FILE -> new StageFieldInputVO("FILE_DRAFT", false, null, true,
                    List.of(), true, null);
            default -> new StageFieldInputVO("SCALAR_VALUES", false, null, false,
                    List.of(), true, null);
        };
    }

    private Map<FieldCode, List<String>> currentFieldValues(
            TenantId tenantId, StageWorkflowRecord record,
            Map<FieldCode, FieldDefinition> definitions, Set<FieldCode> codes) {
        List<FieldCode> scalarCodes = codes.stream().filter(code -> definitions.get(code) != null
                && definitions.get(code).getFieldType()
                != io.github.archetom.crm.domain.model.CustomFieldType.FILE).toList();
        if (scalarCodes.isEmpty()) {
            return Map.of();
        }
        return factResolver.resolve(tenantId, record.getObjectType(), record.getSubjectId(),
                scalarCodes, definitions);
    }

    private boolean canWriteRecord(
            AuthenticatedCaller caller, TenantId tenantId, StageWorkflowRecord record) {
        if (caller == null || !caller.hasAuthority(AuthorityCatalog.WORKFLOWS_WRITE)) {
            return false;
        }
        try {
            shared.requireSameTenant(tenantId, recordAccessGuard.requireVisible(caller,
                    record.getObjectType(), record.getSubjectId(), true));
            return true;
        } catch (ApplicationException exception) {
            if (exception.getErrorCode() == ApplicationErrorCode.ACCESS_DENIED
                    || exception.getErrorCode() == ApplicationErrorCode.RESOURCE_NOT_FOUND) {
                return false;
            }
            throw exception;
        }
    }

    private boolean canAdvance(
            AuthenticatedCaller caller, TenantId tenantId, StageWorkflowRecord record,
            boolean canWrite) {
        if (!canWrite) {
            return false;
        }
        if (record.getStatus() == StageWorkflowRecordStatus.ACTIVE) {
            return true;
        }
        if (record.getStatus() != StageWorkflowRecordStatus.AWAITING_APPROVAL
                || record.getPendingApprovalInstanceId() == null) {
            return false;
        }
        ApprovalInstance approval = approvalInstanceRepository.findById(tenantId,
                record.getPendingApprovalInstanceId()).orElse(null);
        return approval != null && approval.getStatus() != ApprovalInstanceStatus.PENDING;
    }

    private StageApprovalGateVO approvalGate(
            TenantId tenantId, StageWorkflowRecord record, StageDefinition current) {
        if (record.getStatus() == StageWorkflowRecordStatus.AWAITING_APPROVAL
                && record.getPendingApprovalInstanceId() != null) {
            ApprovalInstance approval = approvalInstanceRepository.findById(tenantId,
                    record.getPendingApprovalInstanceId()).orElse(null);
            return new StageApprovalGateVO(approval == null ? "UNAVAILABLE"
                    : approval.getStatus().name(), record.getPendingApprovalInstanceId().getValue(),
                    List.of());
        }
        return new StageApprovalGateVO(current != null && current.approvalDefinitionId() != null
                ? "NOT_STARTED" : "NOT_REQUIRED", null,
                approverSelections(tenantId, current));
    }

    private List<ApprovalStartSelectionVO> approverSelections(
            TenantId tenantId, StageDefinition current) {
        if (current == null || current.approvalDefinitionId() == null) {
            return List.of();
        }
        ApprovalDefinition definition = approvalDefinitionRepository.findById(tenantId,
                current.approvalDefinitionId()).orElse(null);
        if (definition == null || !definition.isActive()) {
            return List.of();
        }
        return definition.getSteps().stream()
                .filter(step -> step.type() == ApprovalStepType.APPLICANT_SELECT)
                .map(step -> new ApprovalStartSelectionVO(step.code(), step.name())).toList();
    }

    private Map<FieldCode, List<String>> approvalFacts(
            TenantId tenantId, ApprovalDefinition definition, Long subjectId,
            Map<FieldCode, List<String>> pendingValues,
            Map<FieldCode, FieldDefinition> fields) {
        LinkedHashSet<FieldCode> codes = new LinkedHashSet<>(pendingValues.keySet());
        definition.getSteps().forEach(step -> step.conditions().forEach(
                condition -> codes.add(condition.fieldCode())));
        if (codes.size() > ApprovalInstance.MAX_ROUTING_FACTS) {
            throw shared.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Stage approval fact fields exceed the supported limit");
        }
        Map<FieldCode, List<String>> authoritative = factResolver.resolve(
                tenantId, definition.getObjectType(), subjectId, codes, fields);
        java.util.LinkedHashMap<FieldCode, List<String>> merged =
                new java.util.LinkedHashMap<>(authoritative);
        merged.putAll(pendingValues);
        return java.util.Collections.unmodifiableMap(merged);
    }

    private static String safePrefix(String value, int maximum) {
        int end = Math.min(maximum, value.length());
        if (end > 0 && end < value.length()
                && Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end);
    }

    private StageWorkflowRecordVO toVO(
            AuthenticatedCaller caller, TenantId tenantId, MemberId memberId, StageWorkflowRecord record) {
        return toVO(caller, tenantId, memberId, record,
                shared.requireWorkflow(tenantId, record.getWorkflowId()));
    }

    static String stageApprovalTitle(String workflowName, String stageName) {
        String title = workflowName + " / " + stageName;
        if (title.length() <= ApprovalInstance.MAX_TITLE_LENGTH) {
            return title;
        }
        String suffix = " / " + stageName;
        int workflowLimit = ApprovalInstance.MAX_TITLE_LENGTH - suffix.length();
        return safePrefix(workflowName, workflowLimit) + suffix;
    }
}
