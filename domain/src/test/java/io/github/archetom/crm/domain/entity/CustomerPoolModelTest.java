package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.CustomerDomainException;
import io.github.archetom.crm.domain.exception.CustomerPoolDomainException;
import io.github.archetom.crm.domain.model.CustomerPoolRuleType;
import io.github.archetom.crm.domain.model.CustomerPoolStatus;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerPoolModelTest {

    @Test
    void poolKeepsAdministrationEligibilityAndRulesBounded() {
        MemberId administrator = new MemberId(11L);
        MemberId member = new MemberId(12L);
        DepartmentId department = new DepartmentId(21L);
        CustomerPool pool = CustomerPool.create(new TenantId(9L), "Strategic pool",
                Set.of(administrator), Set.of(member), Set.of(department),
                5, 7, true,
                List.of(new CustomerPoolRule(CustomerPoolRuleType.STALE_UPDATE, 30)));

        assertTrue(pool.isAdministrator(administrator));
        assertTrue(pool.isEligible(member, null));
        assertTrue(pool.isEligible(new MemberId(13L), department));
        assertFalse(pool.isEligible(new MemberId(14L), null));
        assertThrows(CustomerPoolDomainException.class, () -> pool.changeConfiguration(
                "Broken", Set.of(), Set.of(member), Set.of(), 0, 0, false, List.of()));
        assertThrows(CustomerPoolDomainException.class, () -> pool.changeConfiguration(
                "Strategic pool", Set.of(administrator), Set.of(member), Set.of(),
                5, 7, true, List.of()));
        assertEquals("Strategic pool", pool.getName());

        pool.changeStatus(CustomerPoolStatus.INACTIVE);
        assertEquals(CustomerPoolStatus.INACTIVE, pool.getStatus());
        pool.delete();
        assertThrows(CustomerPoolDomainException.class,
                () -> pool.changeStatus(CustomerPoolStatus.ACTIVE));
    }

    @Test
    void customerPoolTransitionsClearAndRestoreOwnership() {
        MemberId owner = new MemberId(11L);
        Customer customer = Customer.create(new TenantId(9L), owner, "Acme",
                null, null, null, null, null, List.of(), List.of());
        customer.replaceCollaborators(List.of(new CustomerCollaborator(
                new MemberId(12L), io.github.archetom.crm.domain.model.CustomerCollaboratorAccess.READ)));

        customer.enterPool();
        assertEquals(io.github.archetom.crm.domain.model.CustomerStatus.IN_POOL, customer.getStatus());
        assertNull(customer.getOwnerMemberId());
        assertTrue(customer.getCollaborators().isEmpty());
        assertThrows(CustomerDomainException.class, () -> customer.replaceTags(List.of("blocked")));

        customer.leavePool(new MemberId(13L));
        assertTrue(customer.isActive());
        assertEquals(new MemberId(13L), customer.getOwnerMemberId());
        assertThrows(CustomerDomainException.class, () -> customer.leavePool(owner));
    }
}
