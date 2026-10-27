package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Optional;

public interface SavedViewRepository {

    SavedView save(TenantId tenantId, SavedView view);

    SavedView saveAsDefault(TenantId tenantId, SavedView view);

    Optional<SavedView> findById(TenantId tenantId, MemberId memberId, SavedViewId viewId);

    List<SavedView> findAll(
            TenantId tenantId, MemberId memberId, BusinessObjectType objectType,
            boolean includeHidden);

    boolean existsReferencingField(
            TenantId tenantId, BusinessObjectType objectType, FieldCode fieldCode);

    void delete(TenantId tenantId, SavedView view);
}
