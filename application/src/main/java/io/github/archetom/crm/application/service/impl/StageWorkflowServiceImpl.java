package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.StageAdvanceRequest;
import io.github.archetom.crm.api.dto.request.StageCommentCreateRequest;
import io.github.archetom.crm.api.dto.request.StageOutcomeRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowQueryRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowStatusRequest;
import io.github.archetom.crm.application.service.StageWorkflowService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The stage-workflow port, routed to whichever half owns each operation.
 *
 * <p>Defining workflows and running records through them are separate concerns with separate
 * authorities, and the REST layer has always exposed them as two controllers. The application layer
 * did not follow: both lived in one class of 1,888 lines with eighteen entry points and
 * twenty-three collaborators, where the only thing keeping the two apart was which method you
 * happened to be reading.</p>
 *
 * <p>They are now {@link StageWorkflowDefinitionOperations} and
 * {@link StageWorkflowRecordOperations}, over the checks they share in
 * {@link StageWorkflowSharedSupport}. This class keeps the port itself unchanged, so the facade and
 * both controllers are untouched and no caller can tell the difference.</p>
 */
@Service
@RequiredArgsConstructor
public class StageWorkflowServiceImpl implements StageWorkflowService {

    private final StageWorkflowDefinitionOperations definitions;
    private final StageWorkflowRecordOperations records;

    @Override
    public Result<Pager<StageWorkflowVO>> query(AuthenticatedCaller caller, StageWorkflowQueryRequest request) {
        return definitions.query(caller, request);
    }

    @Override
    public Result<StageWorkflowApplicableVO> applicable(AuthenticatedCaller caller, String objectType, Long subjectId) {
        return definitions.applicable(caller, objectType, subjectId);
    }

    @Override
    public Result<StageWorkflowVO> get(AuthenticatedCaller caller, Long workflowId) {
        return definitions.get(caller, workflowId);
    }

    @Override
    public Result<StageWorkflowConfigurationOptionsVO> configurationOptions(AuthenticatedCaller caller, String objectType) {
        return definitions.configurationOptions(caller, objectType);
    }

    @Override
    public Result<Pager<StageFieldCandidateVO>> configurationCandidates(AuthenticatedCaller caller, String candidateType, String keyword, Integer page, Integer size) {
        return definitions.configurationCandidates(caller, candidateType, keyword, page, size);
    }

    @Override
    public Result<StageWorkflowVO> create(AuthenticatedCaller caller, StageWorkflowRequest request) {
        return definitions.create(caller, request);
    }

    @Override
    public Result<StageWorkflowVO> update(AuthenticatedCaller caller, Long workflowId, StageWorkflowRequest request) {
        return definitions.update(caller, workflowId, request);
    }

    @Override
    public Result<StageWorkflowVO> changeStatus(AuthenticatedCaller caller, Long workflowId, StageWorkflowStatusRequest request) {
        return definitions.changeStatus(caller, workflowId, request);
    }

    @Override
    public Result<StageWorkflowRecordVO> start(AuthenticatedCaller caller, Long workflowId, Long subjectId) {
        return records.start(caller, workflowId, subjectId);
    }

    @Override
    public Result<StageWorkflowRecordVO> getRecord(AuthenticatedCaller caller, Long workflowRecordId) {
        return records.getRecord(caller, workflowRecordId);
    }

    @Override
    public Result<Pager<ApprovalSelectionCandidateVO>> advanceSelectionCandidates(AuthenticatedCaller caller, Long workflowRecordId, String selectionCode, Integer page, Integer size) {
        return records.advanceSelectionCandidates(caller, workflowRecordId, selectionCode, page, size);
    }

    @Override
    public Result<Pager<StageFieldCandidateVO>> fieldCandidates(AuthenticatedCaller caller, Long workflowRecordId, String fieldCode, String keyword, Integer page, Integer size) {
        return records.fieldCandidates(caller, workflowRecordId, fieldCode, keyword, page, size);
    }

    @Override
    public Result<FileFieldDraftVO> createFileDraft(AuthenticatedCaller caller, Long workflowRecordId, String fieldCode) {
        return records.createFileDraft(caller, workflowRecordId, fieldCode);
    }

    @Override
    public Result<StageWorkflowRecordVO> advance(AuthenticatedCaller caller, Long workflowRecordId, StageAdvanceRequest request) {
        return records.advance(caller, workflowRecordId, request);
    }

    @Override
    public Result<StageWorkflowRecordVO> outcome(AuthenticatedCaller caller, Long workflowRecordId, StageOutcomeRequest request) {
        return records.outcome(caller, workflowRecordId, request);
    }

    @Override
    public Result<Pager<StageWorkflowCommentVO>> comments(AuthenticatedCaller caller, Long workflowRecordId, Integer page, Integer size) {
        return records.comments(caller, workflowRecordId, page, size);
    }

    @Override
    public Result<StageWorkflowCommentVO> comment(AuthenticatedCaller caller, Long workflowRecordId, StageCommentCreateRequest request) {
        return records.comment(caller, workflowRecordId, request);
    }

    @Override
    public Result<StageWorkflowCommentDeletionVO> deleteComment(AuthenticatedCaller caller, Long workflowRecordId, Long commentId, Long version) {
        return records.deleteComment(caller, workflowRecordId, commentId, version);
    }
}
