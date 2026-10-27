package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.OaFormDefinition;
import io.github.archetom.crm.domain.model.OaFormStatus;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Optional;
import java.util.List;

public interface OaFormRepository {

    OaFormDefinition save(TenantId tenantId, OaFormDefinition form);

    Optional<OaFormDefinition> findById(TenantId tenantId, OaFormId formId);

    Optional<OaFormDefinition> findByIdForUpdate(TenantId tenantId, OaFormId formId);

    PageResult<OaFormDefinition> findPage(
            TenantId tenantId, OaFormStatus status, int page, int size);

    List<OaFormDefinition> findAllActive(TenantId tenantId, int limit);

    long countNonDeleted(TenantId tenantId);

    boolean existsActiveReferencingField(TenantId tenantId, FieldCode fieldCode);

    boolean existsActiveReferencingApprovalDefinition(
            TenantId tenantId, ApprovalDefinitionId definitionId);

    List<OaFormDefinition> findActiveReferencingApprovalDefinition(
            TenantId tenantId, ApprovalDefinitionId definitionId);
}
