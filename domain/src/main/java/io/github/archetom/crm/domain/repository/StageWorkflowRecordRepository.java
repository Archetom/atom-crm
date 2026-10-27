package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.StageWorkflowRecord;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.StageWorkflowId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Optional;

public interface StageWorkflowRecordRepository {
    StageWorkflowRecord save(TenantId tenantId, StageWorkflowRecord record);

    Optional<StageWorkflowRecord> findById(
            TenantId tenantId, StageWorkflowRecordId recordId);

    Optional<StageWorkflowRecord> findByIdForUpdate(
            TenantId tenantId, StageWorkflowRecordId recordId);

    Optional<StageWorkflowRecord> findBySubject(
            TenantId tenantId, BusinessObjectType objectType, Long subjectId);

    boolean existsByWorkflow(TenantId tenantId, StageWorkflowId workflowId);

    boolean existsNonTerminalByWorkflow(
            TenantId tenantId, StageWorkflowId workflowId);
}
