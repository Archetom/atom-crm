package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.vo.AttachmentUploadIntentVO;
import io.github.archetom.crm.application.vo.ProductImageDraftVO;
import io.github.archetom.crm.application.vo.ProductImageUploadStatusVO;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Map;

/** Owns Product primary/detail image staging before the Product aggregate has an ID. */
public interface ProductImageDraftService {
    Result<ProductImageDraftVO> create(AuthenticatedCaller caller);
    Result<AttachmentUploadIntentVO> createIntent(AuthenticatedCaller caller, Long draftId,
                                                  String imageType,
                                                  AttachmentUploadIntentCreateRequest request);
    Result<ProductImageUploadStatusVO> upload(AuthenticatedCaller caller, Long draftId,
                                              String imageType, Long fileId, Long version,
                                              AttachmentByteSource source);
    Result<ProductImageUploadStatusVO> uploadStatus(AuthenticatedCaller caller, Long draftId,
                                                    String imageType, Long fileId);
    Result<Void> withdraw(AuthenticatedCaller caller, Long draftId, Long version);

    /** Called by Product creation inside the same outer write transaction. */
    void consumeCreateDraft(TenantId tenantId, MemberId uploader, Long draftId,
                            Map<String, List<Long>> images, AttachmentTarget target);
}
