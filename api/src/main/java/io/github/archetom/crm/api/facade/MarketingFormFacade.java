package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.MarketingFormCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormQueryRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionQueryRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionTrendRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormUpdateRequest;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.response.MarketingFormResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormConfigurationOptionsResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormSubmissionLinkResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormSubmissionResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormSubmissionTrendResponse;
import io.github.archetom.crm.api.dto.response.PublicMarketingFormResponse;
import io.github.archetom.crm.api.dto.response.PublicMarketingFileDraftResponse;
import io.github.archetom.crm.api.dto.response.PublicMarketingFileDraftWithdrawalResponse;
import io.github.archetom.crm.api.dto.response.PublicMarketingFileUploadStatusResponse;
import io.github.archetom.crm.api.dto.response.PublicMarketingUploadIntentResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.io.InputStream;

public interface MarketingFormFacade {

    Result<MarketingFormConfigurationOptionsResponse> configurationOptions(
            AuthenticatedCaller caller, Long formId, String targetObjectType);

    Result<Pager<MarketingFormResponse>> query(
            AuthenticatedCaller caller, MarketingFormQueryRequest request);

    Result<MarketingFormResponse> get(AuthenticatedCaller caller, Long formId);

    Result<MarketingFormResponse> create(
            AuthenticatedCaller caller, MarketingFormCreateRequest request);

    Result<MarketingFormResponse> update(
            AuthenticatedCaller caller, Long formId, MarketingFormUpdateRequest request);

    Result<MarketingFormResponse> updateStatus(
            AuthenticatedCaller caller, Long formId, MarketingFormStatusUpdateRequest request);

    Result<Void> delete(AuthenticatedCaller caller, Long formId, Long version);

    Result<PublicMarketingFormResponse> getPublic(String publicId);

    Result<MarketingFormSubmissionResponse> submit(
            String publicId, String uploadCapability, MarketingFormSubmissionRequest request);

    Result<PublicMarketingFileDraftResponse> createPublicFileDraft(String publicId);

    Result<PublicMarketingUploadIntentResponse> createPublicFileIntent(
            String publicId, Long draftId, String uploadCapability, String fieldCode,
            AttachmentUploadIntentCreateRequest request);

    Result<PublicMarketingFileUploadStatusResponse> uploadPublicFile(
            String publicId, Long draftId, String uploadCapability, String fieldCode, Long fileId,
            Long version, InputStream content, long declaredLength);

    Result<PublicMarketingFileUploadStatusResponse> publicFileUploadStatus(
            String publicId, Long draftId, String uploadCapability, String fieldCode, Long fileId);

    Result<PublicMarketingFileDraftWithdrawalResponse> withdrawPublicFileDraft(
            String publicId, Long draftId, String uploadCapability);

    Result<MarketingFormSubmissionTrendResponse> submissionTrend(
            AuthenticatedCaller caller, Long formId,
            MarketingFormSubmissionTrendRequest request);

    Result<Pager<MarketingFormSubmissionLinkResponse>> querySubmissions(
            AuthenticatedCaller caller, Long formId,
            MarketingFormSubmissionQueryRequest request);
}
