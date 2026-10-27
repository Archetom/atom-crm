package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.FieldLayoutPreference;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Optional;

public interface FieldLayoutPreferenceRepository {

    FieldLayoutPreference save(TenantId tenantId, FieldLayoutPreference preference);

    Optional<FieldLayoutPreference> findByMember(
            TenantId tenantId, MemberId memberId, BusinessObjectType objectType);

    boolean existsReferencingField(
            TenantId tenantId, BusinessObjectType objectType, FieldCode fieldCode);
}
