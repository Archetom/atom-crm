package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.model.ApprovalDefinitionQuery;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApprovalDefinitionRepository {
    ApprovalDefinition save(TenantId tenantId, ApprovalDefinition definition);

    Optional<ApprovalDefinition> findById(
            TenantId tenantId, ApprovalDefinitionId definitionId);

    List<ApprovalDefinition> findByIds(
            TenantId tenantId, Collection<ApprovalDefinitionId> definitionIds);

    /** Lock one tenant-scoped definition root for a state-changing operation. */
    Optional<ApprovalDefinition> findByIdForUpdate(
            TenantId tenantId, ApprovalDefinitionId definitionId);

    PageResult<ApprovalDefinition> findPage(
            TenantId tenantId, ApprovalDefinitionQuery query);

    List<ApprovalDefinition> findActiveByObjectType(
            TenantId tenantId, BusinessObjectType objectType);

    boolean existsReferencingField(
            TenantId tenantId, BusinessObjectType objectType, FieldCode fieldCode);
}
