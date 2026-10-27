package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.StageAdvanceRequest;
import io.github.archetom.crm.api.dto.request.StageCommentCreateRequest;
import io.github.archetom.crm.api.dto.request.StageOutcomeRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowQueryRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowStatusRequest;
import io.github.archetom.crm.api.dto.response.StageDefinitionResponse;
import io.github.archetom.crm.api.dto.response.StageProgressResponse;
import io.github.archetom.crm.api.dto.response.StageTaskResponse;
import io.github.archetom.crm.api.dto.response.StageApprovalGateResponse;
import io.github.archetom.crm.api.dto.response.StageCurrentStageResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowActionResult;
import io.github.archetom.crm.api.dto.response.StageWorkflowApplicableResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowApplicableItemResponse;
import io.github.archetom.crm.api.dto.response.ApprovalSelectionCandidateResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowCommentDeletionResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowCommentResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowDetailResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowMutationResult;
import io.github.archetom.crm.api.dto.response.StageWorkflowRecordResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowSummaryResponse;
import io.github.archetom.crm.api.dto.response.StageWritableFieldResponse;
import io.github.archetom.crm.api.dto.response.StageFieldOptionResponse;
import io.github.archetom.crm.api.dto.response.StageFieldAttachmentResponse;
import io.github.archetom.crm.api.dto.response.StageFieldInputResponse;
import io.github.archetom.crm.api.dto.response.StageAddressPartResponse;
import io.github.archetom.crm.api.dto.response.StageAddressValueResponse;
import io.github.archetom.crm.api.dto.response.StageOutcomeOptionResponse;
import io.github.archetom.crm.api.dto.response.StageFieldCandidateResponse;
import io.github.archetom.crm.api.dto.response.StageFileFieldDraftResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowConfigurationOptionsResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowConfigurationCandidateResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowConfigurationCandidateType;
import io.github.archetom.crm.api.dto.response.StageWorkflowObjectTypeOptionResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowFieldOptionResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowApprovalDefinitionOptionResponse;
import io.github.archetom.crm.api.facade.StageWorkflowFacade;
import io.github.archetom.crm.application.service.StageWorkflowService;
import io.github.archetom.crm.application.vo.StageWorkflowCommentVO;
import io.github.archetom.crm.application.vo.StageWorkflowRecordVO;
import io.github.archetom.crm.application.vo.StageWorkflowVO;
import io.github.archetom.crm.application.vo.ApprovalSelectionCandidateVO;
import io.github.archetom.crm.application.vo.StageFieldCandidateVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StageWorkflowFacadeImpl implements StageWorkflowFacade {

    private final StageWorkflowService service;

    @Override
    public Result<Pager<StageWorkflowSummaryResponse>> query(
            AuthenticatedCaller caller, StageWorkflowQueryRequest request) {
        return ResultUtil.map(service.query(caller, request), this::summaryPager);
    }

    @Override
    public Result<StageWorkflowApplicableResponse> applicable(
            AuthenticatedCaller caller, String objectType, Long subjectId) {
        return ResultUtil.map(service.applicable(caller, objectType, subjectId), value ->
                new StageWorkflowApplicableResponse(value.objectType(), value.recordId(),
                        value.workflows().stream().map(workflow ->
                                new StageWorkflowApplicableItemResponse(workflow.workflowId(),
                                        workflow.name(), workflow.canStart(),
                                        workflow.blockerCode())).toList()));
    }

    @Override
    public Result<StageWorkflowDetailResponse> get(AuthenticatedCaller caller, Long workflowId) {
        return ResultUtil.map(service.get(caller, workflowId), value -> detail(caller, value));
    }

    @Override
    public Result<StageWorkflowConfigurationOptionsResponse> configurationOptions(
            AuthenticatedCaller caller, String objectType) {
        return ResultUtil.map(service.configurationOptions(caller, objectType), value ->
                new StageWorkflowConfigurationOptionsResponse(value.objectTypes().stream().map(item ->
                        new StageWorkflowObjectTypeOptionResponse(item.code(), item.displayName())).toList(),
                        value.fields().stream().map(item -> new StageWorkflowFieldOptionResponse(
                                item.code(), item.displayName(), item.fieldType(), item.writable(),
                                item.requiredFieldEligible(), item.blockerCode())).toList(),
                        value.approvalDefinitions().stream().map(item ->
                                new StageWorkflowApprovalDefinitionOptionResponse(item.id(), item.name(),
                                        item.version())).toList()));
    }

    @Override
    public Result<Pager<StageWorkflowConfigurationCandidateResponse>> configurationCandidates(
            AuthenticatedCaller caller, StageWorkflowConfigurationCandidateType candidateType,
            String keyword, Integer page, Integer size) {
        return ResultUtil.map(service.configurationCandidates(caller,
                candidateType == null ? null : candidateType.name(), keyword, page, size),
                this::configurationCandidatePager);
    }

    @Override
    public Result<StageWorkflowMutationResult> create(
            AuthenticatedCaller caller, StageWorkflowRequest request) {
        return ResultUtil.map(service.create(caller, request), this::mutation);
    }

    @Override
    public Result<StageWorkflowMutationResult> update(
            AuthenticatedCaller caller, Long workflowId, StageWorkflowRequest request) {
        return ResultUtil.map(service.update(caller, workflowId, request), this::mutation);
    }

    @Override
    public Result<StageWorkflowMutationResult> changeStatus(
            AuthenticatedCaller caller, Long workflowId, StageWorkflowStatusRequest request) {
        return ResultUtil.map(service.changeStatus(caller, workflowId, request), this::mutation);
    }

    @Override
    public Result<StageWorkflowRecordResponse> start(
            AuthenticatedCaller caller, Long workflowId, Long subjectId) {
        return ResultUtil.map(service.start(caller, workflowId, subjectId), this::record);
    }

    @Override
    public Result<StageWorkflowRecordResponse> getRecord(
            AuthenticatedCaller caller, Long workflowRecordId) {
        return ResultUtil.map(service.getRecord(caller, workflowRecordId), this::record);
    }

    @Override
    public Result<Pager<ApprovalSelectionCandidateResponse>> advanceSelectionCandidates(
            AuthenticatedCaller caller, Long workflowRecordId, String selectionCode,
            Integer page, Integer size) {
        return ResultUtil.map(service.advanceSelectionCandidates(caller, workflowRecordId,
                selectionCode, page, size), this::selectionCandidatePager);
    }

    @Override
    public Result<Pager<StageFieldCandidateResponse>> fieldCandidates(
            AuthenticatedCaller caller, Long workflowRecordId, String fieldCode,
            String keyword, Integer page, Integer size) {
        return ResultUtil.map(service.fieldCandidates(caller, workflowRecordId, fieldCode,
                keyword, page, size), this::fieldCandidatePager);
    }

    @Override
    public Result<StageFileFieldDraftResponse> createFileDraft(
            AuthenticatedCaller caller, Long workflowRecordId, String fieldCode) {
        return ResultUtil.map(service.createFileDraft(caller, workflowRecordId, fieldCode), value ->
                new StageFileFieldDraftResponse(value.id(), workflowRecordId, fieldCode,
                        value.version(), value.expiresAt()));
    }

    @Override
    public Result<StageWorkflowActionResult> advance(
            AuthenticatedCaller caller, Long workflowRecordId, StageAdvanceRequest request) {
        return ResultUtil.map(service.advance(caller, workflowRecordId, request), value ->
                new StageWorkflowActionResult(record(value)));
    }

    @Override
    public Result<StageWorkflowActionResult> outcome(
            AuthenticatedCaller caller, Long workflowRecordId, StageOutcomeRequest request) {
        return ResultUtil.map(service.outcome(caller, workflowRecordId, request), value ->
                new StageWorkflowActionResult(record(value)));
    }

    @Override
    public Result<Pager<StageWorkflowCommentResponse>> comments(
            AuthenticatedCaller caller, Long workflowRecordId, Integer page, Integer size) {
        return ResultUtil.map(service.comments(caller, workflowRecordId, page, size),
                this::commentPager);
    }

    @Override
    public Result<StageWorkflowCommentResponse> comment(
            AuthenticatedCaller caller, Long workflowRecordId,
            StageCommentCreateRequest request) {
        return ResultUtil.map(service.comment(caller, workflowRecordId, request), this::comment);
    }

    @Override
    public Result<StageWorkflowCommentDeletionResponse> deleteComment(AuthenticatedCaller caller, Long workflowRecordId,
                                      Long commentId, Long version) {
        return ResultUtil.map(service.deleteComment(caller, workflowRecordId, commentId, version),
                value -> new StageWorkflowCommentDeletionResponse(value.id(), value.deleted()));
    }

    private Pager<StageWorkflowSummaryResponse> summaryPager(Pager<StageWorkflowVO> source) {
        Pager<StageWorkflowSummaryResponse> result = PageUtil.copy(source);
        result.setMeta(null);
        result.setObjectList(source.getObjectList().stream().map(this::summary).toList());
        return result;
    }

    private Pager<StageWorkflowCommentResponse> commentPager(
            Pager<StageWorkflowCommentVO> source) {
        Pager<StageWorkflowCommentResponse> result = PageUtil.copy(source);
        result.setMeta(null);
        result.setObjectList(source.getObjectList().stream().map(this::comment).toList());
        return result;
    }

    private Pager<ApprovalSelectionCandidateResponse> selectionCandidatePager(
            Pager<ApprovalSelectionCandidateVO> source) {
        Pager<ApprovalSelectionCandidateResponse> result = PageUtil.copy(source);
        result.setMeta(null);
        result.setObjectList(source.getObjectList().stream().map(value ->
                new ApprovalSelectionCandidateResponse(value.actorId(), value.displayName())).toList());
        return result;
    }

    private Pager<StageFieldCandidateResponse> fieldCandidatePager(
            Pager<StageFieldCandidateVO> source) {
        Pager<StageFieldCandidateResponse> result = PageUtil.copy(source);
        result.setMeta(null);
        result.setObjectList(source.getObjectList().stream().map(value ->
                new StageFieldCandidateResponse(value.id(), value.displayName(),
                        value.valueType())).toList());
        return result;
    }

    private Pager<StageWorkflowConfigurationCandidateResponse> configurationCandidatePager(
            Pager<StageFieldCandidateVO> source) {
        Pager<StageWorkflowConfigurationCandidateResponse> result = PageUtil.copy(source);
        result.setMeta(null);
        result.setObjectList(source.getObjectList().stream().map(value ->
                new StageWorkflowConfigurationCandidateResponse(value.id(), value.displayName(),
                        switch (value.valueType()) {
                            case "ACTOR_ID" -> StageWorkflowConfigurationCandidateType.MEMBER;
                            case "DEPARTMENT_ID" -> StageWorkflowConfigurationCandidateType.DEPARTMENT;
                            default -> throw new IllegalStateException(
                                    "unexpected stage workflow configuration candidate type");
                        })).toList());
        return result;
    }

    private StageWorkflowSummaryResponse summary(StageWorkflowVO value) {
        return new StageWorkflowSummaryResponse(value.id(), value.name(), value.objectType(),
                value.status(), value.version());
    }

    private StageWorkflowDetailResponse detail(AuthenticatedCaller caller, StageWorkflowVO value) {
        return new StageWorkflowDetailResponse(value.id(), value.objectType(), value.name(),
                value.successName(), value.failureName(), value.status(),
                value.applicableActorIds(), value.applicableDepartmentIds(),
                value.stages().stream().map(stage -> new StageDefinitionResponse(stage.code(),
                        stage.name(), stage.order(), stage.probability(),
                        stage.requiredFieldCodes(), stage.tasks().stream().map(task ->
                        new StageTaskResponse(task.code(), task.name(), task.required())).toList(),
                        stage.approvalDefinitionId(), stage.editable(), stage.blockerCode())).toList(),
                definitionActions(caller, value), allowedStatuses(value, caller), value.version(),
                value.createdTime(), value.updatedTime());
    }

    private StageWorkflowMutationResult mutation(StageWorkflowVO value) {
        return new StageWorkflowMutationResult(value.id(), value.version(), value.status());
    }

    private StageWorkflowRecordResponse record(StageWorkflowRecordVO value) {
        return new StageWorkflowRecordResponse(value.id(), value.workflowId(),
                value.workflowVersion(), value.objectType(), value.subjectId(),
                value.subjectId(), value.currentStageCode(), value.currentStage() == null ? null
                        : new StageCurrentStageResponse(value.currentStage().code(),
                        value.currentStage().name(), value.currentStage().order(),
                        value.currentStage().tasks().stream().map(task -> new StageTaskResponse(
                                task.code(), task.name(), task.required())).toList()), value.status(),
                value.pendingApprovalInstanceId(), value.approvalGate() == null ? null
                        : new StageApprovalGateResponse(value.approvalGate().status(),
                        value.approvalGate().approvalInstanceId(), value.approvalGate()
                        .approverSelections().stream().map(selection ->
                                new io.github.archetom.crm.api.dto.response.ApprovalStartSelectionResponse(
                                        selection.code(), selection.name())).toList()), value.allowedActions(),
                value.allowedOutcomes().stream().map(outcome -> new StageOutcomeOptionResponse(
                        outcome.code(), outcome.label(), outcome.remarkRequired(),
                        outcome.remarkMaxLength())).toList(),
                value.writableFieldCodes(), value.writableFields().stream().map(field ->
                        new StageWritableFieldResponse(field.fieldCode(), field.displayName(),
                                field.fieldType(), field.required(), field.allowedValues().stream()
                                .map(option -> new StageFieldOptionResponse(option.value(),
                                        option.label())).toList(), field.currentValues(),
                                field.currentAddress() == null ? null : new StageAddressValueResponse(
                                        field.currentAddress().country(), field.currentAddress().region(),
                                        field.currentAddress().city(), field.currentAddress().postalCode(),
                                        field.currentAddress().addressLine(),
                                        field.currentAddress().longitude(), field.currentAddress().latitude()),
                                new StageFieldInputResponse(field.input().inputType(),
                                        field.input().candidatesRequired(),
                                        field.input().relationObjectType(),
                                        field.input().fileDraftRequired(),
                                        field.input().addressParts().stream().map(part ->
                                                new StageAddressPartResponse(part.code(),
                                                        part.displayName(), part.required(),
                                                        part.maxLength())).toList(),
                                        field.input().supported(), field.input().blockerCode()),
                                field.masked())).toList(), value.allowedTaskCodes(),
                value.progress().stream().map(progress -> new StageProgressResponse(
                        progress.stageCode(), progress.status(), progress.fieldValues(),
                        progress.completedTaskCodes(), progress.approvalAttempt(),
                        progress.startedTime(), progress.completedTime())).toList(),
                fileFields(value.fileFields()),
                value.outcomeRemark(), value.version(), value.completedTime(), value.updatedTime());
    }

    private java.util.Map<String, java.util.Map<String, java.util.List<StageFieldAttachmentResponse>>>
    fileFields(java.util.Map<String, java.util.Map<String, java.util.List<io.github.archetom.crm.application.vo.AttachmentVO>>> values) {
        if (values == null || values.isEmpty()) return java.util.Map.of();
        return values.entrySet().stream().collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey,
                stage -> stage.getValue().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey,
                        field -> field.getValue().stream().map(file ->
                                new StageFieldAttachmentResponse(file.fileId(), file.displayName(),
                                        file.mediaType(), file.version())).toList(),
                        (left, right) -> left, java.util.LinkedHashMap::new)),
                (left, right) -> left, java.util.LinkedHashMap::new));
    }

    private StageWorkflowCommentResponse comment(StageWorkflowCommentVO value) {
        return new StageWorkflowCommentResponse(value.id(), value.workflowRecordId(),
                value.content(), value.canDelete(), value.version(), value.createdTime());
    }

    private List<String> definitionActions(AuthenticatedCaller caller, StageWorkflowVO value) {
        if (caller == null || !caller.hasAuthority(
                io.github.archetom.crm.domain.policy.AuthorityCatalog.WORKFLOWS_MANAGE)
                || "DELETED".equals(value.status())) {
            return List.of();
        }
        return List.of("UPDATE", "CHANGE_STATUS");
    }

    private List<String> allowedStatuses(StageWorkflowVO value, AuthenticatedCaller caller) {
        if (!definitionActions(caller, value).contains("CHANGE_STATUS")) {
            return List.of();
        }
        return switch (value.status()) {
            case "ACTIVE" -> List.of("INACTIVE", "DELETED");
            case "INACTIVE" -> List.of("ACTIVE", "DELETED");
            default -> List.of();
        };
    }
}
