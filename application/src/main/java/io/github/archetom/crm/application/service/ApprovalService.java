package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ApprovalActionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalBatchActionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionQueryRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionStatusRequest;
import io.github.archetom.crm.api.dto.request.ApprovalPreviewRequest;
import io.github.archetom.crm.api.dto.request.ApprovalQueryRequest;
import io.github.archetom.crm.api.dto.request.ApprovalSubmitRequest;
import io.github.archetom.crm.application.vo.ApprovalDefinitionVO;
import io.github.archetom.crm.application.vo.ApprovalFieldVO;
import io.github.archetom.crm.application.vo.ApprovalInstanceVO;
import io.github.archetom.crm.application.vo.ApprovalInstanceSummaryVO;
import io.github.archetom.crm.application.vo.ApprovalPreviewVO;
import io.github.archetom.crm.application.vo.ApprovalBatchActionResultVO;
import io.github.archetom.crm.application.vo.ApprovalStartOptionsVO;
import io.github.archetom.crm.application.vo.ApprovalSelectionCandidateVO;
import io.github.archetom.crm.application.vo.ApprovalTransferCandidateVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface ApprovalService {
    Result<List<ApprovalFieldVO>> fields(AuthenticatedCaller caller, String objectType);
    Result<Pager<ApprovalDefinitionVO>> queryDefinitions(
            AuthenticatedCaller caller, ApprovalDefinitionQueryRequest request);
    Result<ApprovalDefinitionVO> getDefinition(AuthenticatedCaller caller, Long definitionId);
    Result<ApprovalDefinitionVO> createDefinition(
            AuthenticatedCaller caller, ApprovalDefinitionRequest request);
    Result<ApprovalDefinitionVO> updateDefinition(
            AuthenticatedCaller caller, Long definitionId, ApprovalDefinitionRequest request);
    Result<ApprovalDefinitionVO> changeDefinitionStatus(
            AuthenticatedCaller caller, Long definitionId, ApprovalDefinitionStatusRequest request);
    Result<ApprovalPreviewVO> preview(
            AuthenticatedCaller caller, Long definitionId, ApprovalPreviewRequest request);
    Result<ApprovalInstanceVO> submit(AuthenticatedCaller caller, ApprovalSubmitRequest request);
    Result<ApprovalInstanceVO> getInstance(AuthenticatedCaller caller, Long instanceId);
    Result<Pager<ApprovalInstanceSummaryVO>> queryInstances(
            AuthenticatedCaller caller, ApprovalQueryRequest request);
    Result<Long> pendingCount(AuthenticatedCaller caller);
    Result<ApprovalStartOptionsVO> startOptions(
            AuthenticatedCaller caller, String objectType, Long subjectId);
    Result<Pager<ApprovalSelectionCandidateVO>> startSelectionCandidates(
            AuthenticatedCaller caller, String objectType, Long subjectId, Long definitionId,
            String selectionCode, Integer page, Integer size);
    Result<Pager<ApprovalTransferCandidateVO>> transferCandidates(
            AuthenticatedCaller caller, Long instanceId, Integer page, Integer size);
    Result<ApprovalInstanceVO> act(
            AuthenticatedCaller caller, Long instanceId, ApprovalActionRequest request);
    Result<ApprovalBatchActionResultVO> batchAct(
            AuthenticatedCaller caller, ApprovalBatchActionRequest request);
    Result<ApprovalInstanceVO> withdraw(
            AuthenticatedCaller caller, Long instanceId, Long version);
    Result<ApprovalInstanceVO> cancel(
            AuthenticatedCaller caller, Long instanceId, Long version);
}
