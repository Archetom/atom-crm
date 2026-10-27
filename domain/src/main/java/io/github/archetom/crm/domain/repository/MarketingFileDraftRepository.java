package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.MarketingFileDraft;
import io.github.archetom.crm.domain.model.FileFieldValue;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.MarketingFileDraftId;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped anonymous public MarketingForm FILE draft persistence. */
public interface MarketingFileDraftRepository {
    MarketingFileDraft save(TenantId tenantId, MarketingFileDraft draft);
    Optional<MarketingFileDraft> find(TenantId tenantId, MarketingFileDraftId draftId);
    Optional<MarketingFileDraft> findForUpdate(TenantId tenantId, MarketingFileDraftId draftId);
    Optional<MarketingFileDraft> findForStagedFile(TenantId tenantId, AttachmentFileId fileId);
    long countActiveForFormForUpdate(TenantId tenantId, MarketingFormId formId);
    void lockFormCapacity(TenantId tenantId, MarketingFormId formId);
    void stage(TenantId tenantId, MarketingFileDraftId draftId, FieldDefinition field,
               AttachmentFileId fileId, int ordinal);
    List<StagedFile> findStagedFiles(TenantId tenantId, MarketingFileDraftId draftId);
    List<StagedFile> findStagedFilesForUpdate(TenantId tenantId, MarketingFileDraftId draftId);
    void markStagedBound(TenantId tenantId, MarketingFileDraftId draftId,
                         List<FileFieldValue> values);
    void withdrawStagedFiles(TenantId tenantId, MarketingFileDraftId draftId);

    record StagedFile(FieldDefinition field, AttachmentFile file, int ordinal) { }
}
