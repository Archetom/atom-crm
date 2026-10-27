package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.request.FileFieldDraftCreateRequest;
import io.github.archetom.crm.api.dto.request.OaFileFieldDraftCreateRequest;
import io.github.archetom.crm.api.dto.response.FileFieldDraftResponse;
import io.github.archetom.crm.api.dto.response.AttachmentUploadIntentResponse;
import io.github.archetom.crm.api.dto.response.FileFieldUploadIntentResponse;
import io.github.archetom.crm.api.dto.response.FileFieldUploadedFileResponse;
import io.github.archetom.crm.api.facade.FileFieldDraftFacade;
import io.github.archetom.crm.api.facade.FileFieldUploadContent;
import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.service.FileFieldDraftService;
import io.github.archetom.crm.application.service.FileFieldTransferService;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileFieldDraftFacadeImpl implements FileFieldDraftFacade {
    private final FileFieldDraftService service;
    private final FileFieldTransferService transferService;

    @Override public Result<FileFieldDraftResponse> createCrmDraft(
            AuthenticatedCaller caller, FileFieldDraftCreateRequest request) {
        return ResultUtil.map(service.createCrmDraft(caller, request), value -> new FileFieldDraftResponse(
                value.id(), value.objectType(), value.formId(), value.formVersion(), value.version(), value.expiresAt()));
    }
    @Override public Result<FileFieldDraftResponse> createOaDraft(
            AuthenticatedCaller caller, Long formId, OaFileFieldDraftCreateRequest request) {
        return ResultUtil.map(service.createOaDraft(caller, formId, request), value -> new FileFieldDraftResponse(
                value.id(), value.objectType(), value.formId(), value.formVersion(), value.version(), value.expiresAt()));
    }
    @Override public Result<FileFieldUploadIntentResponse> createDraftIntent(AuthenticatedCaller caller,
            Long draftId, String fieldCode, AttachmentUploadIntentCreateRequest request) {
        return ResultUtil.map(service.createDraftIntent(caller, draftId, fieldCode, request),
                value -> intent(value, fieldCode));
    }
    @Override public Result<AttachmentUploadIntentResponse> createDirectIntent(AuthenticatedCaller caller,
            String targetType, Long targetId, String fieldCode, AttachmentUploadIntentCreateRequest request) {
        return ResultUtil.map(service.createDirectIntent(caller, targetType, targetId, fieldCode, request),
                AttachmentResponseAssembler::toResponse);
    }
    @Override public Result<FileFieldUploadedFileResponse> upload(AuthenticatedCaller caller,
            Long draftId, String fieldCode, Long fileId, Long version, FileFieldUploadContent content) {
        AttachmentByteSource source = new AttachmentByteSource() {
            @Override public java.io.InputStream openStream() throws java.io.IOException {
                return content.openStream();
            }
            @Override public long declaredLength() {
                return content.declaredLength();
            }
        };
        return ResultUtil.map(transferService.upload(caller, draftId, fieldCode, fileId, version, source),
                FileFieldDraftFacadeImpl::uploaded);
    }
    @Override public Result<FileFieldUploadedFileResponse> uploadStatus(AuthenticatedCaller caller,
            Long draftId, String fieldCode, Long fileId) {
        return ResultUtil.map(transferService.uploadStatus(caller, draftId, fieldCode, fileId),
                FileFieldDraftFacadeImpl::uploaded);
    }
    @Override public Result<Void> withdraw(AuthenticatedCaller caller, Long draftId, Long version) {
        return service.withdraw(caller, draftId, version);
    }

    private static FileFieldUploadIntentResponse intent(
            io.github.archetom.crm.application.vo.AttachmentUploadIntentVO value, String fieldCode) {
        if (value.draftId() == null || value.fileId() == null || value.version() == null
                || value.expiresAt() == null) {
            throw new IllegalStateException("file field upload intent is incomplete");
        }
        java.net.URI uploadUrl = java.net.URI.create("/api/v1/file-field-drafts/" + value.draftId()
                + "/fields/" + fieldCode + "/files/" + value.fileId() + "/content?version="
                + value.version());
        return new FileFieldUploadIntentResponse(value.draftId(), fieldCode, value.fileId(), uploadUrl,
                "PUT", java.util.Map.of("Content-Type", "application/octet-stream",
                        "Content-Length", value.expectedBytes().toString()),
                value.expiresAt().toInstant(java.time.ZoneOffset.UTC));
    }

    private static FileFieldUploadedFileResponse uploaded(
            io.github.archetom.crm.application.vo.FileFieldUploadedFileVO value) {
        return new FileFieldUploadedFileResponse(value.fileId(), value.displayName(),
                value.mediaType(), value.size(), value.status());
    }
}
