package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.response.ProductImageDraftResponse;
import io.github.archetom.crm.api.dto.response.ProductImageUploadIntentResponse;
import io.github.archetom.crm.api.dto.response.ProductImageUploadStatusResponse;
import io.github.archetom.crm.api.facade.FileFieldUploadContent;
import io.github.archetom.crm.api.facade.ProductImageDraftFacade;
import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.service.ProductImageDraftService;
import io.github.archetom.crm.application.vo.AttachmentUploadIntentVO;
import io.github.archetom.crm.application.vo.ProductImageUploadStatusVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductImageDraftFacadeImpl implements ProductImageDraftFacade {
    private final ProductImageDraftService service;

    @Override public Result<ProductImageDraftResponse> create(AuthenticatedCaller caller) {
        return ResultUtil.map(service.create(caller), value -> new ProductImageDraftResponse(
                value.draftId(), value.version(), value.expiresAt()));
    }
    @Override public Result<ProductImageUploadIntentResponse> createIntent(AuthenticatedCaller caller,
            Long draftId, String imageType, AttachmentUploadIntentCreateRequest request) {
        return ResultUtil.map(service.createIntent(caller, draftId, imageType, request),
                value -> intent(value, imageType));
    }
    @Override public Result<ProductImageUploadStatusResponse> upload(AuthenticatedCaller caller,
            Long draftId, String imageType, Long fileId, Long version, FileFieldUploadContent content) {
        AttachmentByteSource source = new AttachmentByteSource() {
            @Override public java.io.InputStream openStream() throws java.io.IOException { return content.openStream(); }
            @Override public long declaredLength() { return content.declaredLength(); }
        };
        return ResultUtil.map(service.upload(caller, draftId, imageType, fileId, version, source),
                ProductImageDraftFacadeImpl::status);
    }
    @Override public Result<ProductImageUploadStatusResponse> uploadStatus(AuthenticatedCaller caller,
            Long draftId, String imageType, Long fileId) {
        return ResultUtil.map(service.uploadStatus(caller, draftId, imageType, fileId),
                ProductImageDraftFacadeImpl::status);
    }
    @Override public Result<Void> withdraw(AuthenticatedCaller caller, Long draftId, Long version) {
        return service.withdraw(caller, draftId, version);
    }

    private static ProductImageUploadIntentResponse intent(AttachmentUploadIntentVO value, String imageType) {
        if (value.draftId() == null || value.fileId() == null || value.version() == null || value.expiresAt() == null) {
            throw new IllegalStateException("product image upload intent is incomplete");
        }
        URI url = URI.create("/api/v1/product-image-drafts/" + value.draftId() + "/images/" + imageType
                + "/files/" + value.fileId() + "/content?version=" + value.version());
        return new ProductImageUploadIntentResponse(value.draftId(), imageType, value.fileId(), url, "PUT",
                Map.of("Content-Type", "application/octet-stream", "Content-Length", value.expectedBytes().toString()),
                value.expiresAt().toInstant(ZoneOffset.UTC));
    }
    private static ProductImageUploadStatusResponse status(ProductImageUploadStatusVO value) {
        return new ProductImageUploadStatusResponse(value.draftId(), value.imageType(), value.fileId(),
                value.displayName(), value.mediaType(), value.size(), value.status());
    }
}
