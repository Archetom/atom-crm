package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.response.ProductImageDraftResponse;
import io.github.archetom.crm.api.dto.response.ProductImageUploadIntentResponse;
import io.github.archetom.crm.api.dto.response.ProductImageUploadStatusResponse;

public interface ProductImageDraftFacade {
    Result<ProductImageDraftResponse> create(AuthenticatedCaller caller);
    Result<ProductImageUploadIntentResponse> createIntent(AuthenticatedCaller caller, Long draftId,
                                                          String imageType,
                                                          AttachmentUploadIntentCreateRequest request);
    Result<ProductImageUploadStatusResponse> upload(AuthenticatedCaller caller, Long draftId,
                                                    String imageType, Long fileId, Long version,
                                                    FileFieldUploadContent content);
    Result<ProductImageUploadStatusResponse> uploadStatus(AuthenticatedCaller caller, Long draftId,
                                                          String imageType, Long fileId);
    Result<Void> withdraw(AuthenticatedCaller caller, Long draftId, Long version);
}
