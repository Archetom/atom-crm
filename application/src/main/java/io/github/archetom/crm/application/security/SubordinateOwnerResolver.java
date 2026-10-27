package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/** Resolves a downline scene without accepting ownership scope from the client. */
@Component
@RequiredArgsConstructor
public class SubordinateOwnerResolver {

    private final OrganizationMembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;

    /**
     * Only a descendant-department grant can establish a downline. Tenant-wide access does not
     * imply a reporting relationship, and a self or same-department grant has no descendants.
     */
    public Set<MemberId> resolve(TenantId tenantId, MemberId callerMemberId, DataScope dataScope) {
        if (tenantId == null || callerMemberId == null || dataScope != DataScope.DEPARTMENT_SUBTREE) {
            return Set.of();
        }
        OrganizationMembership membership = membershipRepository.findByMember(tenantId, callerMemberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> new NonRetryableApplicationException(
                        ApplicationErrorCode.ACCESS_DENIED,
                        "Caller is not an active member of this tenant"));
        DepartmentId departmentId = membership.getDepartmentId();
        if (departmentId == null) return Set.of();
        Set<DepartmentId> descendants = departmentRepository.findDescendants(tenantId, departmentId).stream()
                .filter(Department::isActive).map(Department::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (descendants.isEmpty()) return Set.of();
        return membershipRepository.findActiveByDepartments(tenantId, descendants).stream()
                .map(OrganizationMembership::getMemberId)
                .filter(memberId -> !memberId.sameValueAs(callerMemberId))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
