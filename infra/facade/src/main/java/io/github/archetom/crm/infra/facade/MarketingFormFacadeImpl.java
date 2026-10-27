package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.MarketingFormCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormQueryRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionQueryRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionTrendRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormUpdateRequest;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.response.MarketingFormFieldOptionResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormFieldResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormConfigurationFieldResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormConfigurationOptionsResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormSubmissionLinkResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormSubmissionResponse;
import io.github.archetom.crm.api.dto.response.MarketingFormSubmissionTrendResponse;
import io.github.archetom.crm.api.dto.response.PublicMarketingFormResponse;
import io.github.archetom.crm.api.dto.response.PublicMarketingFileDraftResponse;
import io.github.archetom.crm.api.dto.response.PublicMarketingFileDraftWithdrawalResponse;
import io.github.archetom.crm.api.dto.response.PublicMarketingFileUploadStatusResponse;
import io.github.archetom.crm.api.dto.response.PublicMarketingUploadIntentResponse;
import io.github.archetom.crm.api.facade.MarketingFormFacade;
import io.github.archetom.crm.application.service.MarketingFormService;
import io.github.archetom.crm.application.service.MarketingFileDraftService;
import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.vo.MarketingFormFieldVO;
import io.github.archetom.crm.application.vo.MarketingFormSubmissionLinkVO;
import io.github.archetom.crm.application.vo.MarketingFormSubmissionTrendVO;
import io.github.archetom.crm.application.vo.MarketingFormVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MarketingFormFacadeImpl implements MarketingFormFacade {

    private final MarketingFormService service;
    private final MarketingFileDraftService marketingFileDraftService;

    @Override
    public Result<MarketingFormConfigurationOptionsResponse> configurationOptions(
            AuthenticatedCaller caller, Long formId, String targetObjectType) {
        return ResultUtil.map(service.configurationOptions(caller, formId, targetObjectType), value ->
                new MarketingFormConfigurationOptionsResponse(value.fields().stream()
                        .map(field -> new MarketingFormConfigurationFieldResponse(
                                field.fieldCode(), field.displayName(), field.fieldType(),
                                field.required(), field.options().stream().map(option ->
                                        new MarketingFormFieldOptionResponse()
                                                .setValue(option.value()).setLabel(option.label())
                                                .setSortOrder(option.sortOrder())).toList()))
                        .toList()));
    }

    @Override
    public Result<Pager<MarketingFormResponse>> query(
            AuthenticatedCaller caller, MarketingFormQueryRequest request) {
        return ResultUtil.map(service.query(caller, request), source -> {
            Pager<MarketingFormResponse> target = PageUtil.copy(source);
            target.setMeta(null);
            target.setObjectList(source.getObjectList().stream().map(this::response).toList());
            return target;
        });
    }

    @Override
    public Result<MarketingFormResponse> get(
            AuthenticatedCaller caller, Long formId) {
        return ResultUtil.map(service.get(caller, formId), this::response);
    }

    @Override
    public Result<MarketingFormResponse> create(
            AuthenticatedCaller caller, MarketingFormCreateRequest request) {
        return ResultUtil.map(service.create(caller, request), this::response);
    }

    @Override
    public Result<MarketingFormResponse> update(
            AuthenticatedCaller caller, Long formId, MarketingFormUpdateRequest request) {
        return ResultUtil.map(service.update(caller, formId, request), this::response);
    }

    @Override
    public Result<MarketingFormResponse> updateStatus(
            AuthenticatedCaller caller, Long formId,
            MarketingFormStatusUpdateRequest request) {
        return ResultUtil.map(service.updateStatus(caller, formId, request), this::response);
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long formId, Long version) {
        return service.delete(caller, formId, version);
    }

    @Override
    public Result<PublicMarketingFormResponse> getPublic(String publicId) {
        return ResultUtil.map(service.getPublic(publicId), value ->
                new PublicMarketingFormResponse()
                        .setPublicId(value.publicId())
                        .setTitle(value.title())
                        .setDescription(value.description())
                        .setSubmitButtonText(value.submitButtonText())
                        .setSuccessMessage(value.successMessage())
                        .setFields(value.fields().stream().map(this::field).toList()));
    }

    @Override
    public Result<MarketingFormSubmissionResponse> submit(
            String publicId, String uploadCapability, MarketingFormSubmissionRequest request) {
        return ResultUtil.map(service.submit(publicId, uploadCapability, request), value ->
                new MarketingFormSubmissionResponse(value.submissionId(), value.accepted(),
                        value.idempotencyKey()));
    }

    @Override
    public Result<PublicMarketingFileDraftResponse> createPublicFileDraft(String publicId) {
        return ResultUtil.map(marketingFileDraftService.create(publicId), value ->
                new PublicMarketingFileDraftResponse(value.draftId(), value.fieldCodes(),
                        value.uploadCapability(), value.expiresAt()));
    }

    @Override
    public Result<PublicMarketingUploadIntentResponse> createPublicFileIntent(
            String publicId, Long draftId, String uploadCapability, String fieldCode,
            AttachmentUploadIntentCreateRequest request) {
        return ResultUtil.map(marketingFileDraftService.createIntent(publicId, draftId, uploadCapability,
                fieldCode, request), value -> publicIntent(publicId, fieldCode, value));
    }

    @Override
    public Result<PublicMarketingFileUploadStatusResponse> uploadPublicFile(
            String publicId, Long draftId, String uploadCapability, String fieldCode, Long fileId,
            Long version, InputStream content, long declaredLength) {
        AttachmentByteSource source = new AttachmentByteSource() {
            @Override
            public InputStream openStream() {
                return content;
            }

            @Override
            public long declaredLength() {
                return declaredLength;
            }
        };
        return ResultUtil.map(marketingFileDraftService.upload(
                publicId, draftId, uploadCapability, fieldCode, fileId, version, source),
                MarketingFormFacadeImpl::uploadStatus);
    }

    @Override
    public Result<PublicMarketingFileUploadStatusResponse> publicFileUploadStatus(
            String publicId, Long draftId, String uploadCapability, String fieldCode, Long fileId) {
        return ResultUtil.map(marketingFileDraftService.uploadStatus(publicId, draftId, uploadCapability,
                fieldCode, fileId), MarketingFormFacadeImpl::uploadStatus);
    }

    @Override
    public Result<PublicMarketingFileDraftWithdrawalResponse> withdrawPublicFileDraft(
            String publicId, Long draftId, String uploadCapability) {
        return ResultUtil.map(marketingFileDraftService.withdraw(publicId, draftId, uploadCapability),
                value -> new PublicMarketingFileDraftWithdrawalResponse(value.draftId(), value.withdrawn()));
    }

    @Override
    public Result<MarketingFormSubmissionTrendResponse> submissionTrend(
            AuthenticatedCaller caller, Long formId,
            MarketingFormSubmissionTrendRequest request) {
        return ResultUtil.map(service.submissionTrend(caller, formId, request), value ->
                new MarketingFormSubmissionTrendResponse()
                        .setFormId(value.formId())
                        .setFrom(value.from())
                        .setTo(value.to())
                        .setGranularity(value.granularity())
                        .setPoints(value.points().stream()
                                .map(MarketingFormFacadeImpl::trendPoint).toList()));
    }

    @Override
    public Result<Pager<MarketingFormSubmissionLinkResponse>> querySubmissions(
            AuthenticatedCaller caller, Long formId,
            MarketingFormSubmissionQueryRequest request) {
        return ResultUtil.map(service.querySubmissions(caller, formId, request), source -> {
            Pager<MarketingFormSubmissionLinkResponse> target = PageUtil.copy(source);
            target.setMeta(null);
            target.setObjectList(source.getObjectList().stream()
                    .map(MarketingFormFacadeImpl::link).toList());
            return target;
        });
    }

    private MarketingFormResponse response(MarketingFormVO value) {
        return new MarketingFormResponse()
                .setId(value.id())
                .setPublicId(value.publicId())
                .setTargetObjectType(value.targetObjectType())
                .setName(value.name())
                .setTitle(value.title())
                .setDescription(value.description())
                .setSubmitButtonText(value.submitButtonText())
                .setSuccessMessage(value.successMessage())
                .setOwnerActorId(value.ownerActorId())
                .setStatus(value.status())
                .setFields(value.fields().stream().map(this::field).toList())
                .setVersion(value.version())
                .setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }

    private MarketingFormFieldResponse field(MarketingFormFieldVO value) {
        return new MarketingFormFieldResponse()
                .setFieldCode(value.fieldCode())
                .setLabel(value.label())
                .setPlaceholder(value.placeholder())
                .setHelpText(value.helpText())
                .setRequired(value.required())
                .setSortOrder(value.sortOrder())
                .setFieldType(value.fieldType())
                .setOptions(value.options().stream().map(option ->
                        new MarketingFormFieldOptionResponse()
                                .setValue(option.value())
                                .setLabel(option.label())
                                .setSortOrder(option.sortOrder())).toList());
    }

    private static MarketingFormSubmissionTrendResponse
            .MarketingFormSubmissionTrendPointResponse trendPoint(
                    MarketingFormSubmissionTrendVO
                            .MarketingFormSubmissionTrendPointVO value) {
        return new MarketingFormSubmissionTrendResponse
                .MarketingFormSubmissionTrendPointResponse()
                .setPeriodStart(value.periodStart())
                .setAcceptedCount(value.acceptedCount());
    }

    private static MarketingFormSubmissionLinkResponse link(
            MarketingFormSubmissionLinkVO value) {
        return new MarketingFormSubmissionLinkResponse()
                .setSubmissionId(value.submissionId())
                .setTargetObjectType(value.targetObjectType())
                .setTargetRecordId(value.targetRecordId())
                .setFormVersion(value.formVersion())
                .setSubmittedTime(value.submittedTime());
    }

    private static PublicMarketingUploadIntentResponse publicIntent(String publicId, String fieldCode,
                                                                      io.github.archetom.crm.application.vo.AttachmentUploadIntentVO value) {
        if (value.draftId() == null || value.fileId() == null || value.version() == null
                || value.displayName() == null || value.declaredMediaType() == null
                || value.expectedBytes() == null || value.expiresAt() == null) {
            throw new IllegalStateException("public marketing upload intent is incomplete");
        }
        URI uploadUrl = URI.create("/api/v1/open/marketing-forms/" + publicId + "/file-drafts/"
                + value.draftId() + "/fields/" + fieldCode + "/files/" + value.fileId()
                + "/content?version=" + value.version());
        return new PublicMarketingUploadIntentResponse(value.draftId(), fieldCode, value.fileId(),
                uploadUrl, "PUT", Map.of("Content-Type", "application/octet-stream",
                        "Content-Length", value.expectedBytes().toString()),
                io.github.archetom.crm.application.security.MarketingFileDraftCredential.HEADER_NAME,
                value.version(), value.displayName(), value.declaredMediaType(), value.expectedBytes(),
                value.expiresAt());
    }

    private static PublicMarketingFileUploadStatusResponse uploadStatus(
            io.github.archetom.crm.application.vo.PublicMarketingFileUploadStatusVO value) {
        return new PublicMarketingFileUploadStatusResponse(value.draftId(), value.fieldCode(), value.fileId(),
                value.displayName(), value.mediaType(), value.size(), value.version(), value.status());
    }
}
