package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.request.FileFieldDraftCreateRequest;
import io.github.archetom.crm.api.dto.request.OaFileFieldDraftCreateRequest;
import io.github.archetom.crm.application.vo.AttachmentUploadIntentVO;
import io.github.archetom.crm.application.vo.FileFieldDraftVO;

public interface FileFieldDraftService {
    Result<FileFieldDraftVO> createCrmDraft(AuthenticatedCaller caller, FileFieldDraftCreateRequest request);
    Result<FileFieldDraftVO> createStageWorkflowDraft(AuthenticatedCaller caller, Long workflowRecordId,
                                                       String objectType);
    Result<FileFieldDraftVO> createOaDraft(AuthenticatedCaller caller, Long formId,
                                           OaFileFieldDraftCreateRequest request);
    Result<AttachmentUploadIntentVO> createDraftIntent(AuthenticatedCaller caller, Long draftId,
                                                        String fieldCode,
                                                        AttachmentUploadIntentCreateRequest request);
    Result<AttachmentUploadIntentVO> createDirectIntent(AuthenticatedCaller caller, String targetType,
                                                         Long targetId, String fieldCode,
                                                         AttachmentUploadIntentCreateRequest request);
    Result<Void> withdraw(AuthenticatedCaller caller, Long draftId, Long version);
}
