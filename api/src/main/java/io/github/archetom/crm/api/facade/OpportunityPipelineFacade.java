package io.github.archetom.crm.api.facade;

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
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

public interface OpportunityPipelineFacade {
    Result<Pager<OpportunityPipelineResponse>> query(
            AuthenticatedCaller caller, OpportunityPipelineQueryRequest request);
    Result<OpportunityPipelineResponse> get(AuthenticatedCaller caller, Long pipelineId);
    Result<OpportunityPipelineResponse> create(
            AuthenticatedCaller caller, OpportunityPipelineCreateRequest request);
    Result<OpportunityPipelineResponse> update(AuthenticatedCaller caller, Long pipelineId,
                                               OpportunityPipelineUpdateRequest request);
    Result<OpportunityPipelineResponse> changeStatus(AuthenticatedCaller caller, Long pipelineId,
                                                     OpportunityPipelineStatusUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long pipelineId, Long version);
    Result<Pager<OpportunityPipelineStageResponse>> queryStages(
            AuthenticatedCaller caller, Long pipelineId,
            OpportunityPipelineStageQueryRequest request);
    Result<OpportunityPipelineStageResponse> getStage(
            AuthenticatedCaller caller, Long pipelineId, Long stageId);
    Result<OpportunityPipelineStageResponse> createStage(
            AuthenticatedCaller caller, Long pipelineId,
            OpportunityPipelineStageCreateRequest request);
    Result<OpportunityPipelineStageResponse> updateStage(
            AuthenticatedCaller caller, Long pipelineId, Long stageId,
            OpportunityPipelineStageUpdateRequest request);
    Result<OpportunityPipelineStageResponse> changeStageStatus(
            AuthenticatedCaller caller, Long pipelineId, Long stageId,
            OpportunityPipelineStageStatusUpdateRequest request);
    Result<Void> deleteStage(AuthenticatedCaller caller, Long pipelineId, Long stageId,
                             Long version);
}
