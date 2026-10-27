package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.response.AttachmentPageResponse;
import io.github.archetom.crm.api.dto.response.AttachmentResponse;
import io.github.archetom.crm.api.dto.response.AttachmentUploadIntentResponse;
import io.github.archetom.crm.api.dto.response.AttachmentUploadStatusResponse;

public interface AttachmentFacade {
    Result<AttachmentUploadIntentResponse> createIntent(
            AuthenticatedCaller caller, String targetType, Long targetId,
            AttachmentUploadIntentCreateRequest request);
    Result<AttachmentUploadStatusResponse> uploadStatus(
            AuthenticatedCaller caller, String targetType, Long targetId, Long fileId);
    Result<AttachmentPageResponse> query(
            AuthenticatedCaller caller, String targetType, Long targetId, Integer page, Integer size);
    Result<AttachmentPageResponse> query(
            AuthenticatedCaller caller, String targetType, Long targetId, Integer page, Integer size,
            String marketingActivityImageType);
    Result<AttachmentPageResponse> query(
            AuthenticatedCaller caller, String targetType, Long targetId, Integer page, Integer size,
            String marketingActivityImageType, String productImageType);
    Result<AttachmentResponse> get(
            AuthenticatedCaller caller, String targetType, Long targetId, Long fileId);
    Result<Void> withdraw(AuthenticatedCaller caller, String targetType, Long targetId,
                          Long fileId, Long version);
}
