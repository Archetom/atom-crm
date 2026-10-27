package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.model.RoleStatus;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Tenant-scoped persistence port for roles and their grants. */
public interface TenantRoleRepository {

    TenantRole save(TenantId tenantId, TenantRole role);

    Optional<TenantRole> findById(TenantId tenantId, RoleId roleId);

    Optional<TenantRole> findBySystemKey(TenantId tenantId, SystemRoleKey systemKey);

    List<TenantRole> findByIds(TenantId tenantId, Collection<RoleId> roleIds);

    PageResult<TenantRole> findPage(
            TenantId tenantId, RoleStatus status, String keyword, int page, int size);

    void delete(TenantId tenantId, TenantRole role);
}
