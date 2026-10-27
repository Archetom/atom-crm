package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ContactDomainException;
import io.github.archetom.crm.domain.exception.ContactPoolDomainException;
import io.github.archetom.crm.domain.model.ContactPoolRuleType;
import io.github.archetom.crm.domain.model.ContactPoolStatus;
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

class ContactPoolModelTest {

    @Test
    void poolKeepsAdministrationEligibilityAndRulesBounded() {
        MemberId administrator = new MemberId(11L);
        MemberId member = new MemberId(12L);
        DepartmentId department = new DepartmentId(21L);
        ContactPool pool = ContactPool.create(new TenantId(9L), "Strategic pool",
                Set.of(administrator), Set.of(member), Set.of(department),
                5, 7, true,
                List.of(new ContactPoolRule(ContactPoolRuleType.STALE_UPDATE, 30)));

        assertTrue(pool.isAdministrator(administrator));
        assertTrue(pool.isEligible(member, null));
        assertTrue(pool.isEligible(new MemberId(13L), department));
        assertFalse(pool.isEligible(new MemberId(14L), null));
        assertThrows(ContactPoolDomainException.class, () -> pool.changeConfiguration(
                "Broken", Set.of(), Set.of(member), Set.of(), 0, 0, false, List.of()));
        assertThrows(ContactPoolDomainException.class, () -> pool.changeConfiguration(
                "Strategic pool", Set.of(administrator), Set.of(member), Set.of(),
                5, 7, true, List.of()));
        assertEquals("Strategic pool", pool.getName());

        pool.changeStatus(ContactPoolStatus.INACTIVE);
        assertEquals(ContactPoolStatus.INACTIVE, pool.getStatus());
        pool.delete();
        assertThrows(ContactPoolDomainException.class,
                () -> pool.changeStatus(ContactPoolStatus.ACTIVE));
    }

    @Test
    void contactPoolTransitionsClearAndRestoreOwnership() {
        MemberId owner = new MemberId(11L);
        Contact contact = Contact.create(new TenantId(9L), owner, "Acme",
                null, null, null, null, null, List.of(), List.of());
        contact.replaceCollaborators(List.of(new ContactCollaborator(
                new MemberId(12L), io.github.archetom.crm.domain.model.ContactCollaboratorAccess.READ)));

        contact.enterPool();
        assertEquals(io.github.archetom.crm.domain.model.ContactStatus.IN_POOL, contact.getStatus());
        assertNull(contact.getOwnerMemberId());
        assertTrue(contact.getCollaborators().isEmpty());
        assertThrows(ContactDomainException.class, () -> contact.replaceTags(List.of("blocked")));

        contact.leavePool(new MemberId(13L));
        assertTrue(contact.isActive());
        assertEquals(new MemberId(13L), contact.getOwnerMemberId());
        assertThrows(ContactDomainException.class, () -> contact.leavePool(owner));
    }
}
