package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineQueryRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageQueryRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineUpdateRequest;
import io.github.archetom.crm.application.vo.OpportunityPipelineStageVO;
import io.github.archetom.crm.application.vo.OpportunityPipelineVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

public interface OpportunityPipelineService {
    Result<Pager<OpportunityPipelineVO>> query(
            AuthenticatedCaller caller, OpportunityPipelineQueryRequest request);
    Result<OpportunityPipelineVO> get(AuthenticatedCaller caller, Long pipelineId);
    Result<OpportunityPipelineVO> create(
            AuthenticatedCaller caller, OpportunityPipelineCreateRequest request);
    Result<OpportunityPipelineVO> update(AuthenticatedCaller caller, Long pipelineId,
                                         OpportunityPipelineUpdateRequest request);
    Result<OpportunityPipelineVO> changeStatus(AuthenticatedCaller caller, Long pipelineId,
                                               OpportunityPipelineStatusUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long pipelineId, Long version);
    Result<Pager<OpportunityPipelineStageVO>> queryStages(
            AuthenticatedCaller caller, Long pipelineId,
            OpportunityPipelineStageQueryRequest request);
    Result<OpportunityPipelineStageVO> getStage(
            AuthenticatedCaller caller, Long pipelineId, Long stageId);
    Result<OpportunityPipelineStageVO> createStage(
            AuthenticatedCaller caller, Long pipelineId,
            OpportunityPipelineStageCreateRequest request);
    Result<OpportunityPipelineStageVO> updateStage(
            AuthenticatedCaller caller, Long pipelineId, Long stageId,
            OpportunityPipelineStageUpdateRequest request);
    Result<OpportunityPipelineStageVO> changeStageStatus(
            AuthenticatedCaller caller, Long pipelineId, Long stageId,
            OpportunityPipelineStageStatusUpdateRequest request);
    Result<Void> deleteStage(AuthenticatedCaller caller, Long pipelineId, Long stageId,
                             Long version);
}
