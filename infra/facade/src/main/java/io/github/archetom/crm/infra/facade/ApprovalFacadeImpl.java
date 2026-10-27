package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ApprovalActionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalBatchActionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionQueryRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionStatusRequest;
import io.github.archetom.crm.api.dto.request.ApprovalPreviewRequest;
import io.github.archetom.crm.api.dto.request.ApprovalQueryRequest;
import io.github.archetom.crm.api.dto.request.ApprovalSubmitRequest;
import io.github.archetom.crm.api.dto.response.ApprovalConditionResponse;
import io.github.archetom.crm.api.dto.response.ApprovalActionResult;
import io.github.archetom.crm.api.dto.response.ApprovalBatchActionItemResponse;
import io.github.archetom.crm.api.dto.response.ApprovalBatchActionResult;
import io.github.archetom.crm.api.dto.response.ApprovalDefinitionResponse;
import io.github.archetom.crm.api.dto.response.ApprovalFieldResponse;
import io.github.archetom.crm.api.dto.response.ApprovalInstanceResponse;
import io.github.archetom.crm.api.dto.response.ApprovalInstanceSummaryResponse;
import io.github.archetom.crm.api.dto.response.ApprovalPreviewResponse;
import io.github.archetom.crm.api.dto.response.ApprovalPreviewStepResponse;
import io.github.archetom.crm.api.dto.response.ApprovalPendingCountResponse;
import io.github.archetom.crm.api.dto.response.ApprovalStartOptionResponse;
import io.github.archetom.crm.api.dto.response.ApprovalStartOptionsResponse;
import io.github.archetom.crm.api.dto.response.ApprovalSelectionCandidateResponse;
import io.github.archetom.crm.api.dto.response.ApprovalStartSelectionResponse;
import io.github.archetom.crm.api.dto.response.ApprovalTransferCandidateResponse;
import io.github.archetom.crm.api.dto.response.ApprovalStepResponse;
import io.github.archetom.crm.api.dto.response.ApprovalTaskResponse;
import io.github.archetom.crm.api.facade.ApprovalFacade;
import io.github.archetom.crm.application.service.ApprovalService;
import io.github.archetom.crm.application.vo.ApprovalDefinitionVO;
import io.github.archetom.crm.application.vo.ApprovalInstanceVO;
import io.github.archetom.crm.application.vo.ApprovalInstanceSummaryVO;
import io.github.archetom.crm.application.vo.ApprovalBatchActionResultVO;
import io.github.archetom.crm.application.vo.ApprovalStartOptionsVO;
import io.github.archetom.crm.application.vo.ApprovalSelectionCandidateVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApprovalFacadeImpl implements ApprovalFacade {

    private final ApprovalService service;

    @Override
    public Result<List<ApprovalFieldResponse>> fields(
            AuthenticatedCaller caller, String objectType) {
        return ResultUtil.map(service.fields(caller, objectType), values -> values.stream()
                .map(value -> new ApprovalFieldResponse(value.fieldCode(), value.displayName(),
                        value.fieldType(), value.required())).toList());
    }

    @Override
    public Result<Pager<ApprovalDefinitionResponse>> queryDefinitions(
            AuthenticatedCaller caller, ApprovalDefinitionQueryRequest request) {
        return ResultUtil.map(service.queryDefinitions(caller, request), this::definitionPager);
    }

    @Override
    public Result<ApprovalDefinitionResponse> getDefinition(
            AuthenticatedCaller caller, Long definitionId) {
        return ResultUtil.map(service.getDefinition(caller, definitionId), this::definition);
    }

    @Override
    public Result<ApprovalDefinitionResponse> createDefinition(
            AuthenticatedCaller caller, ApprovalDefinitionRequest request) {
        return ResultUtil.map(service.createDefinition(caller, request), this::definition);
    }

    @Override
    public Result<ApprovalDefinitionResponse> updateDefinition(
            AuthenticatedCaller caller, Long definitionId, ApprovalDefinitionRequest request) {
        return ResultUtil.map(service.updateDefinition(caller, definitionId, request), this::definition);
    }

    @Override
    public Result<ApprovalDefinitionResponse> changeDefinitionStatus(
            AuthenticatedCaller caller, Long definitionId,
            ApprovalDefinitionStatusRequest request) {
        return ResultUtil.map(service.changeDefinitionStatus(caller, definitionId, request),
                this::definition);
    }

    @Override
    public Result<ApprovalPreviewResponse> preview(
            AuthenticatedCaller caller, Long definitionId, ApprovalPreviewRequest request) {
        return ResultUtil.map(service.preview(caller, definitionId, request), value ->
                new ApprovalPreviewResponse(value.definitionId(), value.definitionVersion(),
                        value.subjectId(), value.executable(), value.blockerCode(), value.steps().stream().map(step ->
                        new ApprovalPreviewStepResponse(step.code(), step.name(),
                                step.selectionRequired())).toList()));
    }

    @Override
    public Result<ApprovalInstanceResponse> submit(
            AuthenticatedCaller caller, ApprovalSubmitRequest request) {
        return ResultUtil.map(service.submit(caller, request), this::instance);
    }

    @Override
    public Result<ApprovalInstanceResponse> getInstance(
            AuthenticatedCaller caller, Long instanceId) {
        return ResultUtil.map(service.getInstance(caller, instanceId), this::instance);
    }

    @Override
    public Result<Pager<ApprovalInstanceSummaryResponse>> queryInstances(
            AuthenticatedCaller caller, ApprovalQueryRequest request) {
        return ResultUtil.map(service.queryInstances(caller, request), this::summaryPager);
    }

    @Override
    public Result<ApprovalPendingCountResponse> pendingCount(AuthenticatedCaller caller) {
        return ResultUtil.map(service.pendingCount(caller), ApprovalPendingCountResponse::new);
    }

    @Override
    public Result<ApprovalStartOptionsResponse> startOptions(
            AuthenticatedCaller caller, String objectType, Long subjectId) {
        return ResultUtil.map(service.startOptions(caller, objectType, subjectId), this::startOptions);
    }

    @Override
    public Result<Pager<ApprovalSelectionCandidateResponse>> startSelectionCandidates(
            AuthenticatedCaller caller, String objectType, Long subjectId, Long definitionId,
            String selectionCode, Integer page, Integer size) {
        return ResultUtil.map(service.startSelectionCandidates(caller, objectType, subjectId,
                definitionId, selectionCode, page, size), this::selectionCandidatePager);
    }

    @Override
    public Result<Pager<ApprovalTransferCandidateResponse>> transferCandidates(
            AuthenticatedCaller caller, Long instanceId, Integer page, Integer size) {
        return ResultUtil.map(service.transferCandidates(caller, instanceId, page, size), values -> {
            Pager<ApprovalTransferCandidateResponse> result = PageUtil.copy(values);
            result.setMeta(null);
            result.setObjectList(values.getObjectList().stream()
                    .map(value -> new ApprovalTransferCandidateResponse(
                            value.actorId(), value.displayName())).toList());
            return result;
        });
    }

    @Override
    public Result<ApprovalActionResult> act(
            AuthenticatedCaller caller, Long instanceId, ApprovalActionRequest request) {
        return ResultUtil.map(service.act(caller, instanceId, request), value ->
                new ApprovalActionResult(instance(value)));
    }

    @Override
    public Result<ApprovalBatchActionResult> batchAct(
            AuthenticatedCaller caller, ApprovalBatchActionRequest request) {
        return ResultUtil.map(service.batchAct(caller, request), this::batchResult);
    }

    @Override
    public Result<ApprovalActionResult> withdraw(
            AuthenticatedCaller caller, Long instanceId, Long version) {
        return ResultUtil.map(service.withdraw(caller, instanceId, version), value ->
                new ApprovalActionResult(instance(value)));
    }

    @Override
    public Result<ApprovalActionResult> cancel(
            AuthenticatedCaller caller, Long instanceId, Long version) {
        return ResultUtil.map(service.cancel(caller, instanceId, version), value ->
                new ApprovalActionResult(instance(value)));
    }

    private Pager<ApprovalDefinitionResponse> definitionPager(Pager<ApprovalDefinitionVO> source) {
        Pager<ApprovalDefinitionResponse> result = PageUtil.copy(source);
        result.setMeta(null);
        result.setObjectList(source.getObjectList().stream().map(this::definition).toList());
        return result;
    }

    private Pager<ApprovalInstanceSummaryResponse> summaryPager(
            Pager<ApprovalInstanceSummaryVO> source) {
        Pager<ApprovalInstanceSummaryResponse> result = PageUtil.copy(source);
        result.setMeta(null);
        result.setObjectList(source.getObjectList().stream().map(this::summary).toList());
        return result;
    }

    private ApprovalDefinitionResponse definition(ApprovalDefinitionVO value) {
        return new ApprovalDefinitionResponse(value.id(), value.objectType(), value.name(),
                value.description(), value.status(), value.repeatMode(),
                value.initiatorActorIds(), value.initiatorDepartmentIds(),
                value.managerActorIds(), value.steps().stream().map(step ->
                new ApprovalStepResponse(step.code(), step.name(), step.order(), step.type(),
                        step.mode(), step.fallback(), step.memberActorIds(), step.roleIds(),
                        step.conditions().stream().map(condition ->
                                new ApprovalConditionResponse(condition.fieldCode(),
                                        condition.operator(), condition.values())).toList()))
                        .toList(), value.version(), value.createdTime(), value.updatedTime());
    }

    private ApprovalInstanceResponse instance(ApprovalInstanceVO value) {
        return new ApprovalInstanceResponse(value.id(), value.version(), value.status(), value.title(),
                value.allowedActions(),
                value.tasks().stream().map(task -> new ApprovalTaskResponse(task.stepOrder(),
                        task.stepName(), task.status(), task.actedTime())).toList());
    }

    private ApprovalInstanceSummaryResponse summary(ApprovalInstanceSummaryVO value) {
        return new ApprovalInstanceSummaryResponse(value.id(), value.title(), value.status(),
                value.allowedActions(), value.version());
    }

    private ApprovalBatchActionResult batchResult(ApprovalBatchActionResultVO value) {
        return new ApprovalBatchActionResult(value.atomic(), value.applied(), value.items().stream()
                .map(item -> new ApprovalBatchActionItemResponse(item.instanceId(), item.status(),
                        item.version(), item.errCode() == null ? "NONE" : item.errCode())).toList());
    }

    private ApprovalStartOptionsResponse startOptions(ApprovalStartOptionsVO value) {
        return new ApprovalStartOptionsResponse(value.objectType(), value.subjectId(),
                value.options().stream().map(option -> new ApprovalStartOptionResponse(
                        option.definitionId(), option.name(), option.selections().stream()
                                .map(selection -> new ApprovalStartSelectionResponse(
                                        selection.code(), selection.name())).toList())).toList());
    }

    private Pager<ApprovalSelectionCandidateResponse> selectionCandidatePager(
            Pager<ApprovalSelectionCandidateVO> source) {
        Pager<ApprovalSelectionCandidateResponse> result = PageUtil.copy(source);
        result.setMeta(null);
        result.setObjectList(source.getObjectList().stream().map(value ->
                new ApprovalSelectionCandidateResponse(value.actorId(), value.displayName())).toList());
        return result;
    }
}
