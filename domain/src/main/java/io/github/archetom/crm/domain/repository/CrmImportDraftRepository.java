package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.CrmImportDraft;
import io.github.archetom.crm.domain.model.CrmImportObjectType;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.CrmImportDraftId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for a private CRM import draft root. */
public interface CrmImportDraftRepository {
    CrmImportDraft save(TenantId tenantId, CrmImportDraft draft);

    Optional<CrmImportDraft> find(TenantId tenantId, CrmImportDraftId draftId);

    Optional<CrmImportDraft> findForUpdate(TenantId tenantId, CrmImportDraftId draftId);

    /**
     * Bounded, cross-tenant expiry discovery. Callers must re-lock each returned root before
     * mutating it because another command may consume or withdraw it after this read.
     */
    List<CrmImportDraft> findDueActive(LocalDateTime deadline, int limit);

    /** Completed, caller-owned import summaries only; the source attachment is never returned. */
    PageResult<CrmImportDraft> findCompletedByUploader(
            TenantId tenantId, MemberId uploaderMemberId, CrmImportObjectType objectType,
            LocalDateTime completedSince, long page, long size);
}
