package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.StageAdvanceRequest;
import io.github.archetom.crm.api.dto.request.StageCommentCreateRequest;
import io.github.archetom.crm.api.dto.request.StageOutcomeRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowQueryRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowStatusRequest;
import io.github.archetom.crm.application.vo.StageWorkflowCommentVO;
import io.github.archetom.crm.application.vo.StageWorkflowCommentDeletionVO;
import io.github.archetom.crm.application.vo.StageWorkflowRecordVO;
import io.github.archetom.crm.application.vo.StageWorkflowVO;
import io.github.archetom.crm.application.vo.StageWorkflowApplicableVO;
import io.github.archetom.crm.application.vo.ApprovalSelectionCandidateVO;
import io.github.archetom.crm.application.vo.StageFieldCandidateVO;
import io.github.archetom.crm.application.vo.FileFieldDraftVO;
import io.github.archetom.crm.application.vo.StageWorkflowConfigurationOptionsVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

public interface StageWorkflowService {
    Result<Pager<StageWorkflowVO>> query(
            AuthenticatedCaller caller, StageWorkflowQueryRequest request);
    Result<StageWorkflowApplicableVO> applicable(
            AuthenticatedCaller caller, String objectType, Long subjectId);
    Result<StageWorkflowVO> get(AuthenticatedCaller caller, Long workflowId);
    Result<StageWorkflowConfigurationOptionsVO> configurationOptions(
            AuthenticatedCaller caller, String objectType);
    Result<Pager<StageFieldCandidateVO>> configurationCandidates(
            AuthenticatedCaller caller, String candidateType, String keyword, Integer page, Integer size);
    Result<StageWorkflowVO> create(AuthenticatedCaller caller, StageWorkflowRequest request);
    Result<StageWorkflowVO> update(
            AuthenticatedCaller caller, Long workflowId, StageWorkflowRequest request);
    Result<StageWorkflowVO> changeStatus(
            AuthenticatedCaller caller, Long workflowId, StageWorkflowStatusRequest request);
    Result<StageWorkflowRecordVO> start(
            AuthenticatedCaller caller, Long workflowId, Long subjectId);
    Result<StageWorkflowRecordVO> getRecord(
            AuthenticatedCaller caller, Long workflowRecordId);
    Result<Pager<ApprovalSelectionCandidateVO>> advanceSelectionCandidates(
            AuthenticatedCaller caller, Long workflowRecordId, String selectionCode,
            Integer page, Integer size);
    Result<Pager<StageFieldCandidateVO>> fieldCandidates(
            AuthenticatedCaller caller, Long workflowRecordId, String fieldCode,
            String keyword, Integer page, Integer size);
    Result<FileFieldDraftVO> createFileDraft(
            AuthenticatedCaller caller, Long workflowRecordId, String fieldCode);
    Result<StageWorkflowRecordVO> advance(
            AuthenticatedCaller caller, Long workflowRecordId, StageAdvanceRequest request);
    Result<StageWorkflowRecordVO> outcome(
            AuthenticatedCaller caller, Long workflowRecordId, StageOutcomeRequest request);
    Result<Pager<StageWorkflowCommentVO>> comments(
            AuthenticatedCaller caller, Long workflowRecordId, Integer page, Integer size);
    Result<StageWorkflowCommentVO> comment(
            AuthenticatedCaller caller, Long workflowRecordId, StageCommentCreateRequest request);
    Result<StageWorkflowCommentDeletionVO> deleteComment(AuthenticatedCaller caller, Long workflowRecordId,
                               Long commentId, Long version);
}
