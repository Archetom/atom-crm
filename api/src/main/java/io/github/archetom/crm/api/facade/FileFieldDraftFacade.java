package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.request.FileFieldDraftCreateRequest;
import io.github.archetom.crm.api.dto.request.OaFileFieldDraftCreateRequest;
import io.github.archetom.crm.api.dto.response.FileFieldDraftResponse;
import io.github.archetom.crm.api.dto.response.AttachmentUploadIntentResponse;
import io.github.archetom.crm.api.dto.response.FileFieldUploadIntentResponse;
import io.github.archetom.crm.api.dto.response.FileFieldUploadedFileResponse;

public interface FileFieldDraftFacade {
    Result<FileFieldDraftResponse> createCrmDraft(AuthenticatedCaller caller, FileFieldDraftCreateRequest request);
    Result<FileFieldDraftResponse> createOaDraft(AuthenticatedCaller caller, Long formId,
                                                 OaFileFieldDraftCreateRequest request);
    Result<FileFieldUploadIntentResponse> createDraftIntent(AuthenticatedCaller caller, Long draftId,
                                                            String fieldCode, AttachmentUploadIntentCreateRequest request);
    Result<AttachmentUploadIntentResponse> createDirectIntent(AuthenticatedCaller caller, String targetType,
                                                               Long targetId, String fieldCode,
                                                               AttachmentUploadIntentCreateRequest request);
    Result<FileFieldUploadedFileResponse> upload(AuthenticatedCaller caller, Long draftId,
                                                 String fieldCode, Long fileId, Long version,
                                                 FileFieldUploadContent content);
    Result<FileFieldUploadedFileResponse> uploadStatus(AuthenticatedCaller caller, Long draftId,
                                                        String fieldCode, Long fileId);
    Result<Void> withdraw(AuthenticatedCaller caller, Long draftId, Long version);
}
