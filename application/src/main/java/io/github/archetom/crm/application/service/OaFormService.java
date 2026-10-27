package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OaFormCreateRequest;
import io.github.archetom.crm.api.dto.request.OaFormQueryRequest;
import io.github.archetom.crm.api.dto.request.OaFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OaFormUpdateRequest;
import io.github.archetom.crm.application.vo.OaFormVO;
import io.github.archetom.crm.application.vo.OaFormConfigurationOptionsVO;
import io.github.archetom.crm.application.vo.OaFormFieldCandidateVO;
import io.github.archetom.crm.application.vo.ApprovalSelectionCandidateVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface OaFormService {
    Result<OaFormConfigurationOptionsVO> configurationOptions(AuthenticatedCaller caller);

    Result<Pager<OaFormVO>> query(AuthenticatedCaller caller, OaFormQueryRequest request);

    Result<OaFormVO> get(AuthenticatedCaller caller, Long formId);

    Result<List<OaFormVO>> available(AuthenticatedCaller caller);

    Result<OaFormVO> getAvailable(AuthenticatedCaller caller, Long formId);

    Result<Pager<OaFormFieldCandidateVO>> fieldCandidates(
            AuthenticatedCaller caller, Long formId, String fieldCode,
            String keyword, Integer page, Integer size);

    Result<Pager<ApprovalSelectionCandidateVO>> approvalSelectionCandidates(
            AuthenticatedCaller caller, Long formId, String selectionCode,
            String keyword, Integer page, Integer size);

    Result<OaFormVO> create(AuthenticatedCaller caller, OaFormCreateRequest request);

    Result<OaFormVO> update(
            AuthenticatedCaller caller, Long formId, OaFormUpdateRequest request);

    Result<OaFormVO> updateStatus(
            AuthenticatedCaller caller, Long formId, OaFormStatusUpdateRequest request);

    Result<Void> delete(AuthenticatedCaller caller, Long formId, Long version);
}
