package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.MarketingActivityImageType;
import io.github.archetom.crm.domain.model.ProductImageType;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Optional;
import java.util.List;

/** Tenant-scoped attachment aggregate and relation-history port. */
public interface AttachmentFileRepository {

    AttachmentFile save(TenantId tenantId, AttachmentFile file);

    /** Persists a new immutable target-first intent while the caller already holds its target lock. */
    default AttachmentFile saveIntent(TenantId tenantId, AttachmentTarget target, AttachmentFile file) {
        return saveIntent(tenantId, target, file, null, null);
    }

    /**
     * Persists an intent with optional, server-validated marketing image grouping. The grouping
     * is relation metadata and therefore cannot be reused by another target or file field.
     */
    default AttachmentFile saveIntent(TenantId tenantId, AttachmentTarget target, AttachmentFile file,
                                      MarketingActivityImageType marketingActivityImageType) {
        return saveIntent(tenantId, target, file, marketingActivityImageType, null);
    }

    AttachmentFile saveIntent(TenantId tenantId, AttachmentTarget target, AttachmentFile file,
                              MarketingActivityImageType marketingActivityImageType,
                              ProductImageType productImageType);

    /** Binds a clean private staged Product image to the just-created Product under its root lock. */
    void bindStagedProductImage(TenantId tenantId, AttachmentTarget target, AttachmentFileId fileId,
                                ProductImageType productImageType);

    /** Persists a one-shot B24c FILE-field candidate without creating a general attachment link. */
    AttachmentFile saveStagedIntent(TenantId tenantId, AttachmentFile file);

    /** B24c workers use this for an unbound candidate before its owning record exists. */
    Optional<AttachmentFile> findByIdForUpdate(TenantId tenantId, AttachmentFileId fileId);

    Optional<AttachmentFile> findByTargetForUpdate(
            TenantId tenantId, AttachmentTarget target, AttachmentFileId fileId);

    Optional<AttachmentTarget> findTargetForFileForUpdate(TenantId tenantId, AttachmentFileId fileId);

    /** Routes a worker to the typed target before it acquires the target-root lock. */
    Optional<AttachmentTarget> findTargetForFile(TenantId tenantId, AttachmentFileId fileId);

    void withdrawRelation(TenantId tenantId, AttachmentTarget target, AttachmentFileId fileId);

    Optional<AttachmentFile> findAvailableByTarget(
            TenantId tenantId, AttachmentTarget target, AttachmentFileId fileId);

    default PageResult<AttachmentFile> findAvailablePage(
            TenantId tenantId, AttachmentTarget target, long page, long size) {
        return findAvailablePage(tenantId, target, page, size, null, null);
    }

    default PageResult<AttachmentFile> findAvailablePage(
            TenantId tenantId, AttachmentTarget target, long page, long size,
            MarketingActivityImageType marketingActivityImageType) {
        return findAvailablePage(tenantId, target, page, size, marketingActivityImageType, null);
    }

    PageResult<AttachmentFile> findAvailablePage(
            TenantId tenantId, AttachmentTarget target, long page, long size,
            MarketingActivityImageType marketingActivityImageType, ProductImageType productImageType);

    /**
     * The activity root is already locked by the caller. Counting active upload intents as well
     * as published files prevents concurrent uploads from exceeding a per-group image limit.
     */
    long countActiveMarketingActivityImagesForUpdate(
            TenantId tenantId, AttachmentTarget target, MarketingActivityImageType marketingActivityImageType);

    long countActiveProductImagesForUpdate(
            TenantId tenantId, AttachmentTarget target, ProductImageType productImageType);

    boolean existsHistoryByTargetForUpdate(TenantId tenantId, AttachmentTarget target);

    /** Parent deletion holds the target root and withdraws every active relation in file-ID order. */
    List<AttachmentFile> findActiveByTargetForUpdate(TenantId tenantId, AttachmentTarget target);

    /**
     * Publication admission reads at most the supplied bound while holding the target root.
     * A caller asking for 101 rows can distinguish a valid 100-file set from an overflow.
     */
    List<AttachmentFile> findActiveByTargetForUpdate(
            TenantId tenantId, AttachmentTarget target, int limit);
}
