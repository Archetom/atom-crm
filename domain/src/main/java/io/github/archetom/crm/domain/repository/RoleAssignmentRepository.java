package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.RoleAssignment;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.List;

/** Tenant-scoped persistence port for explicit member-role assignments. */
public interface RoleAssignmentRepository {

    RoleAssignment save(TenantId tenantId, RoleAssignment assignment);

    List<RoleAssignment> findByMember(TenantId tenantId, MemberId memberId);

    List<RoleAssignment> findByMembers(TenantId tenantId, Collection<MemberId> memberIds);

    PageResult<RoleAssignment> findByRole(TenantId tenantId, RoleId roleId, int page, int size);

    List<MemberId> findActiveMembersByRoles(
            TenantId tenantId, Collection<RoleId> roleIds, int limit);

    boolean exists(TenantId tenantId, MemberId memberId, RoleId roleId);

    long countByRole(TenantId tenantId, RoleId roleId);

    long countActiveMembersByRole(TenantId tenantId, RoleId roleId);

    boolean delete(TenantId tenantId, MemberId memberId, RoleId roleId);
}
