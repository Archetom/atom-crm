package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.OrganizationDomainException;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganizationModelTest {

    @Test
    void departmentCannotBecomeItsOwnParentOrChangeAfterDeletion() {
        Department department = Department.create(new TenantId(9L), null, "Sales");
        department.onPersisted(new DepartmentId(4L), 0L, null, null);

        assertThrows(OrganizationDomainException.class, () -> department.moveTo(new DepartmentId(4L)));

        department.delete();

        assertThrows(OrganizationDomainException.class, () -> department.rename("Revenue"));
    }

    @Test
    void membershipRequestCanOnlyBeWithdrawnByItsApplicant() {
        MembershipRequest request = MembershipRequest.submit(
                new MemberId(7L), new TenantId(9L), null, "Please add me");

        assertThrows(OrganizationDomainException.class, () -> request.withdraw(new MemberId(8L)));

        request.withdraw(new MemberId(7L));

        assertTrue(!request.isPending());
    }

    @Test
    void suspendedMembershipCannotChangeDepartments() {
        OrganizationMembership membership = OrganizationMembership.activate(
                new TenantId(9L), new MemberId(7L), null);
        membership.suspend();

        assertThrows(OrganizationDomainException.class,
                () -> membership.assignDepartment(new DepartmentId(3L)));
    }

    @Test
    void memberProfileIsBoundToTheVerifiedActorIdentifier() {
        MemberProfile profile = MemberProfile.register(new MemberId(7L), "Taylor", null, null);

        assertEquals(7L, profile.getId().getValue());
    }
}
