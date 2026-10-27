package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.ContractVisibility;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.DepartmentStatus;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.OpportunityVisibility;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.model.ReceivableVisibility;
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

class DashboardVisibilityNarrowerTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final DepartmentId ROOT = new DepartmentId(11L);
    private static final DepartmentId CHILD = new DepartmentId(12L);

    @Test
    void tenantAuthorizationIsReallyRestrictedToTheCurrentActiveSubtree() {
        OrganizationMembershipRepository memberships =
                mock(OrganizationMembershipRepository.class);
        DepartmentRepository departments = mock(DepartmentRepository.class);
        when(memberships.findByMember(TENANT, MEMBER)).thenReturn(Optional.of(
                OrganizationMembership.activate(TENANT, MEMBER, ROOT)));
        when(departments.findDescendants(TENANT, ROOT)).thenReturn(List.of(
                department(CHILD, DepartmentStatus.ACTIVE),
                department(new DepartmentId(13L), DepartmentStatus.INACTIVE)));
        DashboardVisibilityNarrower narrower =
                new DashboardVisibilityNarrower(memberships, departments);
        var source = new OpportunityAccessResolver.OpportunityAccess(
                TENANT, MEMBER, new OpportunityVisibility(
                DataScope.TENANT, MEMBER, Set.of(), OpportunityAccessMode.READ));

        var narrowed = narrower.narrow(source, DashboardDataScope.DEPARTMENT_SUBTREE);

        assertEquals(DataScope.DEPARTMENT_SUBTREE,
                narrowed.visibility().dataScope());
        assertEquals(Set.of(ROOT, CHILD), narrowed.visibility().ownerDepartmentIds());
        assertEquals(OpportunityAccessMode.READ, narrowed.visibility().accessMode());
    }

    @Test
    void selfPreferenceKeepsSourceSpecificReadSemanticsWithoutDepartmentQueries() {
        OrganizationMembershipRepository memberships =
                mock(OrganizationMembershipRepository.class);
        DepartmentRepository departments = mock(DepartmentRepository.class);
        when(memberships.findByMember(TENANT, MEMBER)).thenReturn(Optional.of(
                OrganizationMembership.activate(TENANT, MEMBER, ROOT)));
        DashboardVisibilityNarrower narrower =
                new DashboardVisibilityNarrower(memberships, departments);
        var source = new ContractAccessResolver.ContractAccess(
                TENANT, MEMBER, new ContractVisibility(
                DataScope.DEPARTMENT_SUBTREE, MEMBER, Set.of(ROOT, CHILD),
                ContractAccessMode.READ));

        var narrowed = narrower.narrow(source, DashboardDataScope.SELF);

        assertEquals(DataScope.SELF, narrowed.visibility().dataScope());
        assertEquals(Set.of(), narrowed.visibility().ownerDepartmentIds());
        assertEquals(ContractAccessMode.READ, narrowed.visibility().accessMode());
        verify(departments, never()).findDescendants(TENANT, ROOT);
    }

    @Test
    void missingDepartmentDowngradesDepartmentPreferenceToSelf() {
        OrganizationMembershipRepository memberships =
                mock(OrganizationMembershipRepository.class);
        DepartmentRepository departments = mock(DepartmentRepository.class);
        when(memberships.findByMember(TENANT, MEMBER)).thenReturn(Optional.of(
                OrganizationMembership.activate(TENANT, MEMBER, null)));
        DashboardVisibilityNarrower narrower =
                new DashboardVisibilityNarrower(memberships, departments);
        var source = new ReceivableAccessResolver.ReceivableAccess(
                TENANT, MEMBER, new ReceivableVisibility(
                DataScope.TENANT, MEMBER, Set.of(), ReceivableAccessMode.READ));

        var narrowed = narrower.narrow(source, DashboardDataScope.DEPARTMENT);

        assertEquals(DataScope.SELF, narrowed.visibility().dataScope());
        assertEquals(Set.of(), narrowed.visibility().ownerDepartmentIds());
    }

    private Department department(DepartmentId id, DepartmentStatus status) {
        return Department.reconstitute(new Department.Snapshot(
                id, TENANT, ROOT, "Department " + id.getValue(), status,
                0L, null, null));
    }
}
