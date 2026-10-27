package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.StageWorkflowDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.StageWorkflowQuery;
import io.github.archetom.crm.domain.valueobject.StageWorkflowId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Optional;

public interface StageWorkflowDefinitionRepository {
    StageWorkflowDefinition save(TenantId tenantId, StageWorkflowDefinition workflow);

    Optional<StageWorkflowDefinition> findById(
            TenantId tenantId, StageWorkflowId workflowId);

    PageResult<StageWorkflowDefinition> findPage(
            TenantId tenantId, StageWorkflowQuery query);

    List<StageWorkflowDefinition> findActiveByObjectType(
            TenantId tenantId, BusinessObjectType objectType);

    boolean existsReferencingField(
            TenantId tenantId, BusinessObjectType objectType, FieldCode fieldCode);

    boolean existsReferencingApprovalDefinition(
            TenantId tenantId, ApprovalDefinitionId approvalDefinitionId);

    List<StageWorkflowDefinition> findReferencingApprovalDefinition(
            TenantId tenantId, ApprovalDefinitionId approvalDefinitionId);
}
