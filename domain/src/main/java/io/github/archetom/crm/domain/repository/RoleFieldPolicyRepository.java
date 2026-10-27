package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.RoleFieldPolicy;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleFieldPolicyRepository {

    RoleFieldPolicy save(TenantId tenantId, RoleFieldPolicy policy);

    Optional<RoleFieldPolicy> find(
            TenantId tenantId, RoleId roleId, BusinessObjectType objectType);

    List<RoleFieldPolicy> findByRoles(
            TenantId tenantId, Collection<RoleId> roleIds, BusinessObjectType objectType);

    List<RoleFieldPolicy> findByRolesAndFields(
            TenantId tenantId, Collection<RoleId> roleIds, BusinessObjectType objectType,
            Collection<FieldCode> fieldCodes);
}
