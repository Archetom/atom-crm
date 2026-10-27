package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FieldDefinitionRepository {

    FieldDefinition save(TenantId tenantId, FieldDefinition field);

    Optional<FieldDefinition> findById(
            TenantId tenantId, BusinessObjectType objectType, FieldDefinitionId fieldId);

    Optional<FieldDefinition> findByCode(
            TenantId tenantId, BusinessObjectType objectType, FieldCode code);

    List<FieldDefinition> findByCodes(
            TenantId tenantId, BusinessObjectType objectType, Collection<FieldCode> codes);

    List<FieldDefinition> findAll(TenantId tenantId, BusinessObjectType objectType,
                                  boolean includeInactive);
}
