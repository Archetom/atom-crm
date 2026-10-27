package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

/** Tenant-scoped persistence port for actor membership and tenant access. */
public interface OrganizationMembershipRepository {

    OrganizationMembership save(TenantId tenantId, OrganizationMembership membership);

    Optional<OrganizationMembership> findByMember(TenantId tenantId, MemberId memberId);

    Optional<OrganizationMembership> findByMemberForShare(
            TenantId tenantId, MemberId memberId);

    List<OrganizationMembership> findByMembers(TenantId tenantId, Collection<MemberId> memberIds);

    /** Active members assigned to one of the server-resolved department IDs. */
    List<OrganizationMembership> findActiveByDepartments(
            TenantId tenantId, Collection<DepartmentId> departmentIds);

    List<OrganizationMembership> findActiveByMember(MemberId memberId);

    boolean existsActiveInTenant(TenantId tenantId);

    boolean existsActiveInDepartment(TenantId tenantId, DepartmentId departmentId);

    boolean existsInDepartment(TenantId tenantId, DepartmentId departmentId);
}
