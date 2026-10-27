package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.MarketingFormCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormQueryRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionQueryRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionTrendRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormUpdateRequest;
import io.github.archetom.crm.application.vo.MarketingFormSubmissionLinkVO;
import io.github.archetom.crm.application.vo.MarketingFormSubmissionVO;
import io.github.archetom.crm.application.vo.MarketingFormSubmissionTrendVO;
import io.github.archetom.crm.application.vo.MarketingFormConfigurationOptionsVO;
import io.github.archetom.crm.application.vo.MarketingFormVO;
import io.github.archetom.crm.application.vo.PublicMarketingFormVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

public interface MarketingFormService {

    Result<MarketingFormConfigurationOptionsVO> configurationOptions(
            AuthenticatedCaller caller, Long formId, String targetObjectType);

    Result<Pager<MarketingFormVO>> query(
            AuthenticatedCaller caller, MarketingFormQueryRequest request);

    Result<MarketingFormVO> get(AuthenticatedCaller caller, Long formId);

    Result<MarketingFormVO> create(
            AuthenticatedCaller caller, MarketingFormCreateRequest request);

    Result<MarketingFormVO> update(
            AuthenticatedCaller caller, Long formId, MarketingFormUpdateRequest request);

    Result<MarketingFormVO> updateStatus(
            AuthenticatedCaller caller, Long formId, MarketingFormStatusUpdateRequest request);

    Result<Void> delete(AuthenticatedCaller caller, Long formId, Long version);

    Result<PublicMarketingFormVO> getPublic(String publicId);

    Result<MarketingFormSubmissionVO> submit(
            String publicId, String uploadCapability, MarketingFormSubmissionRequest request);

    default Result<MarketingFormSubmissionVO> submit(
            String publicId, MarketingFormSubmissionRequest request) {
        return submit(publicId, null, request);
    }

    Result<MarketingFormSubmissionTrendVO> submissionTrend(
            AuthenticatedCaller caller, Long formId,
            MarketingFormSubmissionTrendRequest request);

    Result<Pager<MarketingFormSubmissionLinkVO>> querySubmissions(
            AuthenticatedCaller caller, Long formId,
            MarketingFormSubmissionQueryRequest request);
}
