package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ApprovalActionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalBatchActionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionQueryRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionStatusRequest;
import io.github.archetom.crm.api.dto.request.ApprovalPreviewRequest;
import io.github.archetom.crm.api.dto.request.ApprovalQueryRequest;
import io.github.archetom.crm.api.dto.request.ApprovalSubmitRequest;
import io.github.archetom.crm.api.dto.response.ApprovalDefinitionResponse;
import io.github.archetom.crm.api.dto.response.ApprovalActionResult;
import io.github.archetom.crm.api.dto.response.ApprovalBatchActionResult;
import io.github.archetom.crm.api.dto.response.ApprovalFieldResponse;
import io.github.archetom.crm.api.dto.response.ApprovalInstanceResponse;
import io.github.archetom.crm.api.dto.response.ApprovalInstanceSummaryResponse;
import io.github.archetom.crm.api.dto.response.ApprovalPreviewResponse;
import io.github.archetom.crm.api.dto.response.ApprovalPendingCountResponse;
import io.github.archetom.crm.api.dto.response.ApprovalStartOptionsResponse;
import io.github.archetom.crm.api.dto.response.ApprovalSelectionCandidateResponse;
import io.github.archetom.crm.api.dto.response.ApprovalTransferCandidateResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface ApprovalFacade {
    Result<List<ApprovalFieldResponse>> fields(AuthenticatedCaller caller, String objectType);
    Result<Pager<ApprovalDefinitionResponse>> queryDefinitions(
            AuthenticatedCaller caller, ApprovalDefinitionQueryRequest request);
    Result<ApprovalDefinitionResponse> getDefinition(AuthenticatedCaller caller, Long definitionId);
    Result<ApprovalDefinitionResponse> createDefinition(
            AuthenticatedCaller caller, ApprovalDefinitionRequest request);
    Result<ApprovalDefinitionResponse> updateDefinition(
            AuthenticatedCaller caller, Long definitionId, ApprovalDefinitionRequest request);
    Result<ApprovalDefinitionResponse> changeDefinitionStatus(
            AuthenticatedCaller caller, Long definitionId, ApprovalDefinitionStatusRequest request);
    Result<ApprovalPreviewResponse> preview(
            AuthenticatedCaller caller, Long definitionId, ApprovalPreviewRequest request);
    Result<ApprovalInstanceResponse> submit(AuthenticatedCaller caller, ApprovalSubmitRequest request);
    Result<ApprovalInstanceResponse> getInstance(AuthenticatedCaller caller, Long instanceId);
    Result<Pager<ApprovalInstanceSummaryResponse>> queryInstances(
            AuthenticatedCaller caller, ApprovalQueryRequest request);
    Result<ApprovalPendingCountResponse> pendingCount(AuthenticatedCaller caller);
    Result<ApprovalStartOptionsResponse> startOptions(
            AuthenticatedCaller caller, String objectType, Long subjectId);
    Result<Pager<ApprovalSelectionCandidateResponse>> startSelectionCandidates(
            AuthenticatedCaller caller, String objectType, Long subjectId, Long definitionId,
            String selectionCode, Integer page, Integer size);
    Result<Pager<ApprovalTransferCandidateResponse>> transferCandidates(
            AuthenticatedCaller caller, Long instanceId, Integer page, Integer size);
    Result<ApprovalActionResult> act(
            AuthenticatedCaller caller, Long instanceId, ApprovalActionRequest request);
    Result<ApprovalBatchActionResult> batchAct(
            AuthenticatedCaller caller, ApprovalBatchActionRequest request);
    Result<ApprovalActionResult> withdraw(
            AuthenticatedCaller caller, Long instanceId, Long version);
    Result<ApprovalActionResult> cancel(
            AuthenticatedCaller caller, Long instanceId, Long version);
}
