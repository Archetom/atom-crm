package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.StageAdvanceRequest;
import io.github.archetom.crm.api.dto.request.StageCommentCreateRequest;
import io.github.archetom.crm.api.dto.request.StageOutcomeRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowQueryRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowStatusRequest;
import io.github.archetom.crm.api.dto.response.StageWorkflowActionResult;
import io.github.archetom.crm.api.dto.response.StageWorkflowApplicableResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowCommentDeletionResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowCommentResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowDetailResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowMutationResult;
import io.github.archetom.crm.api.dto.response.StageWorkflowRecordResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowSummaryResponse;
import io.github.archetom.crm.api.dto.response.ApprovalSelectionCandidateResponse;
import io.github.archetom.crm.api.dto.response.StageFieldCandidateResponse;
import io.github.archetom.crm.api.dto.response.StageFileFieldDraftResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowConfigurationOptionsResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowConfigurationCandidateResponse;
import io.github.archetom.crm.api.dto.response.StageWorkflowConfigurationCandidateType;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

public interface StageWorkflowFacade {
    Result<Pager<StageWorkflowSummaryResponse>> query(
            AuthenticatedCaller caller, StageWorkflowQueryRequest request);
    Result<StageWorkflowApplicableResponse> applicable(
            AuthenticatedCaller caller, String objectType, Long subjectId);
    Result<StageWorkflowDetailResponse> get(AuthenticatedCaller caller, Long workflowId);
    Result<StageWorkflowConfigurationOptionsResponse> configurationOptions(
            AuthenticatedCaller caller, String objectType);
    Result<Pager<StageWorkflowConfigurationCandidateResponse>> configurationCandidates(
            AuthenticatedCaller caller, StageWorkflowConfigurationCandidateType candidateType,
            String keyword, Integer page, Integer size);
    Result<StageWorkflowMutationResult> create(AuthenticatedCaller caller, StageWorkflowRequest request);
    Result<StageWorkflowMutationResult> update(
            AuthenticatedCaller caller, Long workflowId, StageWorkflowRequest request);
    Result<StageWorkflowMutationResult> changeStatus(
            AuthenticatedCaller caller, Long workflowId, StageWorkflowStatusRequest request);
    Result<StageWorkflowRecordResponse> start(
            AuthenticatedCaller caller, Long workflowId, Long subjectId);
    Result<StageWorkflowRecordResponse> getRecord(
            AuthenticatedCaller caller, Long workflowRecordId);
    Result<Pager<ApprovalSelectionCandidateResponse>> advanceSelectionCandidates(
            AuthenticatedCaller caller, Long workflowRecordId, String selectionCode,
            Integer page, Integer size);
    Result<Pager<StageFieldCandidateResponse>> fieldCandidates(
            AuthenticatedCaller caller, Long workflowRecordId, String fieldCode,
            String keyword, Integer page, Integer size);
    Result<StageFileFieldDraftResponse> createFileDraft(
            AuthenticatedCaller caller, Long workflowRecordId, String fieldCode);
    Result<StageWorkflowActionResult> advance(
            AuthenticatedCaller caller, Long workflowRecordId, StageAdvanceRequest request);
    Result<StageWorkflowActionResult> outcome(
            AuthenticatedCaller caller, Long workflowRecordId, StageOutcomeRequest request);
    Result<Pager<StageWorkflowCommentResponse>> comments(
            AuthenticatedCaller caller, Long workflowRecordId, Integer page, Integer size);
    Result<StageWorkflowCommentResponse> comment(
            AuthenticatedCaller caller, Long workflowRecordId, StageCommentCreateRequest request);
    Result<StageWorkflowCommentDeletionResponse> deleteComment(AuthenticatedCaller caller, Long workflowRecordId,
                               Long commentId, Long version);
}
