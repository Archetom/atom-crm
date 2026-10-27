package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.Optional;

/** Persistence port for global profiles bound to verified actors. */
public interface MemberProfileRepository {

    MemberProfile save(MemberProfile profile);

    Optional<MemberProfile> findById(MemberId memberId);

    /** Searches profiles that have an active membership in the explicit tenant. */
    PageResult<MemberProfile> findActiveByTenant(TenantId tenantId, String keyword, int page, int size);

    /** Searches active profiles assigned to one visible department in the explicit tenant. */
    PageResult<MemberProfile> findActiveByDepartment(
            TenantId tenantId, DepartmentId departmentId, String keyword, int page, int size);

    /** Searches active profiles assigned to the explicit server-resolved department scope. */
    PageResult<MemberProfile> findActiveByDepartments(
            TenantId tenantId, Collection<DepartmentId> departmentIds, String keyword, int page, int size);
}
