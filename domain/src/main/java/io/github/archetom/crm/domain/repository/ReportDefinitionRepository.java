package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.ReportDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReportDefinitionId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Optional;

public interface ReportDefinitionRepository {

    ReportDefinition save(TenantId tenantId, ReportDefinition definition);

    Optional<ReportDefinition> findActiveById(
            TenantId tenantId, MemberId memberId, ReportDefinitionId definitionId);

    PageResult<ReportDefinition> findActivePage(
            TenantId tenantId, MemberId memberId, BusinessObjectType objectType,
            String normalizedKeyword, int page, int size);

    long countActive(TenantId tenantId, MemberId memberId, BusinessObjectType objectType);

    boolean existsReferencingField(
            TenantId tenantId, BusinessObjectType objectType, FieldCode fieldCode);
}
