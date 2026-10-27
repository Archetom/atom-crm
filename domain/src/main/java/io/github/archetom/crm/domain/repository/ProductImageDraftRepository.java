package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.ProductImageDraft;
import io.github.archetom.crm.domain.model.ProductImageType;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.ProductImageDraftId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped staging records for Product images that have no parent Product yet. */
public interface ProductImageDraftRepository {
    ProductImageDraft save(TenantId tenantId, ProductImageDraft draft);
    Optional<ProductImageDraft> find(TenantId tenantId, ProductImageDraftId draftId);
    Optional<ProductImageDraft> findForUpdate(TenantId tenantId, ProductImageDraftId draftId);
    Optional<ProductImageDraft> findForStagedFile(TenantId tenantId, AttachmentFileId fileId);
    void stage(TenantId tenantId, ProductImageDraftId draftId, ProductImageType imageType,
               AttachmentFileId fileId, int ordinal);
    List<StagedFile> findStagedFiles(TenantId tenantId, ProductImageDraftId draftId);
    List<StagedFile> findStagedFilesForUpdate(TenantId tenantId, ProductImageDraftId draftId);
    void markStagedBound(TenantId tenantId, ProductImageDraftId draftId, List<StagedFile> values);
    void withdrawStagedFiles(TenantId tenantId, ProductImageDraftId draftId);

    record StagedFile(ProductImageType imageType, AttachmentFile file, int ordinal) { }
}
