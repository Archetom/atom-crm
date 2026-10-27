package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OaFormCreateRequest;
import io.github.archetom.crm.api.dto.request.OaFormQueryRequest;
import io.github.archetom.crm.api.dto.request.OaFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OaFormUpdateRequest;
import io.github.archetom.crm.api.dto.response.OaFormResponse;
import io.github.archetom.crm.api.dto.response.OaFormConfigurationOptionsResponse;
import io.github.archetom.crm.api.dto.response.OaFormFieldCandidateResponse;
import io.github.archetom.crm.api.dto.response.ApprovalSelectionCandidateResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface OaFormFacade {
    Result<OaFormConfigurationOptionsResponse> configurationOptions(AuthenticatedCaller caller);

    Result<Pager<OaFormResponse>> query(
            AuthenticatedCaller caller, OaFormQueryRequest request);

    Result<OaFormResponse> get(AuthenticatedCaller caller, Long formId);

    Result<List<OaFormResponse>> available(AuthenticatedCaller caller);

    Result<OaFormResponse> getAvailable(AuthenticatedCaller caller, Long formId);

    Result<Pager<OaFormFieldCandidateResponse>> fieldCandidates(
            AuthenticatedCaller caller, Long formId, String fieldCode,
            String keyword, Integer page, Integer size);

    Result<Pager<ApprovalSelectionCandidateResponse>> approvalSelectionCandidates(
            AuthenticatedCaller caller, Long formId, String selectionCode,
            String keyword, Integer page, Integer size);

    Result<OaFormResponse> create(
            AuthenticatedCaller caller, OaFormCreateRequest request);

    Result<OaFormResponse> update(
            AuthenticatedCaller caller, Long formId, OaFormUpdateRequest request);

    Result<OaFormResponse> updateStatus(
            AuthenticatedCaller caller, Long formId, OaFormStatusUpdateRequest request);

    Result<Void> delete(AuthenticatedCaller caller, Long formId, Long version);
}
