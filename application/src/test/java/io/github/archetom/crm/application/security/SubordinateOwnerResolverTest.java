package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.DepartmentStatus;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubordinateOwnerResolverTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId CALLER = new MemberId(7L);
    private static final MemberId SUBORDINATE = new MemberId(8L);
    private static final DepartmentId ROOT = new DepartmentId(11L);
    private static final DepartmentId CHILD = new DepartmentId(12L);

    @Test
    void resolvesOnlyActiveMembersOfActiveDescendantDepartments() {
        OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
        DepartmentRepository departments = mock(DepartmentRepository.class);
        when(memberships.findByMember(TENANT, CALLER)).thenReturn(Optional.of(
                OrganizationMembership.activate(TENANT, CALLER, ROOT)));
        when(departments.findDescendants(TENANT, ROOT)).thenReturn(List.of(
                department(CHILD, DepartmentStatus.ACTIVE),
                department(new DepartmentId(13L), DepartmentStatus.INACTIVE)));
        when(memberships.findActiveByDepartments(TENANT, Set.of(CHILD))).thenReturn(List.of(
                OrganizationMembership.activate(TENANT, CALLER, CHILD),
                OrganizationMembership.activate(TENANT, SUBORDINATE, CHILD)));

        Set<MemberId> owners = new SubordinateOwnerResolver(memberships, departments)
                .resolve(TENANT, CALLER, DataScope.DEPARTMENT_SUBTREE);

        assertEquals(Set.of(SUBORDINATE), owners);
        verify(memberships).findActiveByDepartments(TENANT, Set.of(CHILD));
    }

    @Test
    void doesNotTurnTenantOrSelfScopeIntoAReportingRelationship() {
        OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
        DepartmentRepository departments = mock(DepartmentRepository.class);
        SubordinateOwnerResolver resolver = new SubordinateOwnerResolver(memberships, departments);

        assertEquals(Set.of(), resolver.resolve(TENANT, CALLER, DataScope.TENANT));
        assertEquals(Set.of(), resolver.resolve(TENANT, CALLER, DataScope.SELF));
        verify(memberships, never()).findByMember(TENANT, CALLER);
        verify(departments, never()).findDescendants(TENANT, ROOT);
    }

    private Department department(DepartmentId id, DepartmentStatus status) {
        return Department.reconstitute(new Department.Snapshot(
                id, TENANT, ROOT, "Department " + id.getValue(), status, 0L, null, null));
    }
}
