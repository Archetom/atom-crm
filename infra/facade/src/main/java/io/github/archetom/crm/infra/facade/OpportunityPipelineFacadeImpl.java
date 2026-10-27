package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineQueryRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageQueryRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineUpdateRequest;
import io.github.archetom.crm.api.dto.response.OpportunityPipelineResponse;
import io.github.archetom.crm.api.dto.response.OpportunityPipelineStageResponse;
import io.github.archetom.crm.api.facade.OpportunityPipelineFacade;
import io.github.archetom.crm.application.service.OpportunityPipelineService;
import io.github.archetom.crm.application.vo.OpportunityPipelineStageVO;
import io.github.archetom.crm.application.vo.OpportunityPipelineVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpportunityPipelineFacadeImpl implements OpportunityPipelineFacade {

    private final OpportunityPipelineService service;

    @Override
    public Result<Pager<OpportunityPipelineResponse>> query(
            AuthenticatedCaller caller, OpportunityPipelineQueryRequest request) {
        return ResultUtil.map(service.query(caller, request), this::pipelinePager);
    }

    @Override
    public Result<OpportunityPipelineResponse> get(AuthenticatedCaller caller, Long pipelineId) {
        return ResultUtil.map(service.get(caller, pipelineId), this::pipeline);
    }

    @Override
    public Result<OpportunityPipelineResponse> create(
            AuthenticatedCaller caller, OpportunityPipelineCreateRequest request) {
        return ResultUtil.map(service.create(caller, request), this::pipeline);
    }

    @Override
    public Result<OpportunityPipelineResponse> update(
            AuthenticatedCaller caller, Long pipelineId, OpportunityPipelineUpdateRequest request) {
        return ResultUtil.map(service.update(caller, pipelineId, request), this::pipeline);
    }

    @Override
    public Result<OpportunityPipelineResponse> changeStatus(
            AuthenticatedCaller caller, Long pipelineId,
            OpportunityPipelineStatusUpdateRequest request) {
        return ResultUtil.map(service.changeStatus(caller, pipelineId, request), this::pipeline);
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long pipelineId, Long version) {
        return service.delete(caller, pipelineId, version);
    }

    @Override
    public Result<Pager<OpportunityPipelineStageResponse>> queryStages(
            AuthenticatedCaller caller, Long pipelineId,
            OpportunityPipelineStageQueryRequest request) {
        return ResultUtil.map(service.queryStages(caller, pipelineId, request), this::stagePager);
    }

    @Override
    public Result<OpportunityPipelineStageResponse> getStage(
            AuthenticatedCaller caller, Long pipelineId, Long stageId) {
        return ResultUtil.map(service.getStage(caller, pipelineId, stageId), this::stage);
    }

    @Override
    public Result<OpportunityPipelineStageResponse> createStage(
            AuthenticatedCaller caller, Long pipelineId,
            OpportunityPipelineStageCreateRequest request) {
        return ResultUtil.map(service.createStage(caller, pipelineId, request), this::stage);
    }

    @Override
    public Result<OpportunityPipelineStageResponse> updateStage(
            AuthenticatedCaller caller, Long pipelineId, Long stageId,
            OpportunityPipelineStageUpdateRequest request) {
        return ResultUtil.map(service.updateStage(caller, pipelineId, stageId, request),
                this::stage);
    }

    @Override
    public Result<OpportunityPipelineStageResponse> changeStageStatus(
            AuthenticatedCaller caller, Long pipelineId, Long stageId,
            OpportunityPipelineStageStatusUpdateRequest request) {
        return ResultUtil.map(service.changeStageStatus(caller, pipelineId, stageId, request),
                this::stage);
    }

    @Override
    public Result<Void> deleteStage(
            AuthenticatedCaller caller, Long pipelineId, Long stageId, Long version) {
        return service.deleteStage(caller, pipelineId, stageId, version);
    }

    private Pager<OpportunityPipelineResponse> pipelinePager(Pager<OpportunityPipelineVO> source) {
        Pager<OpportunityPipelineResponse> result = PageUtil.copy(source);
        result.setMeta(null);
        result.setObjectList(source.getObjectList().stream().map(this::pipeline).toList());
        return result;
    }

    private Pager<OpportunityPipelineStageResponse> stagePager(
            Pager<OpportunityPipelineStageVO> source) {
        Pager<OpportunityPipelineStageResponse> result = PageUtil.copy(source);
        result.setMeta(null);
        result.setObjectList(source.getObjectList().stream().map(this::stage).toList());
        return result;
    }

    private OpportunityPipelineResponse pipeline(OpportunityPipelineVO value) {
        return new OpportunityPipelineResponse(value.id(), value.code(), value.name(),
                value.displayOrder(), value.status(), value.version(), value.createdTime(),
                value.updatedTime());
    }

    private OpportunityPipelineStageResponse stage(OpportunityPipelineStageVO value) {
        return new OpportunityPipelineStageResponse(value.id(), value.pipelineCode(),
                value.code(), value.name(), value.displayOrder(), value.probability(),
                value.status(), value.version(), value.createdTime(), value.updatedTime());
    }
}
