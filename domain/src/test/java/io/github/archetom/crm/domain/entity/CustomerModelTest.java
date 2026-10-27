package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.CustomerDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.CustomerCollaboratorAccess;
import io.github.archetom.crm.domain.model.CustomerDealStatus;
import io.github.archetom.crm.domain.policy.CustomerFieldValuePolicy;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerModelTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId OWNER = new MemberId(11L);

    @Test
    void aggregateNormalizesBoundedValuesAndKeepsFailedChangesAtomic() {
        Customer customer = customer();
        customer.onPersisted(new CustomerId(21L), 0L, null, null);
        customer.replaceTags(List.of(
                " Priority ", "priority", "Enterprise", "e", "E", "é"));
        customer.replaceCollaborators(List.of(new CustomerCollaborator(
                new MemberId(12L), CustomerCollaboratorAccess.WRITE)));

        assertEquals(List.of("Priority", "Enterprise", "e", "é"), customer.getTags());
        assertEquals("hq", customer.getAddresses().getFirst().key());
        assertEquals("contact@example.invalid", customer.getEmail());
        assertThrows(CustomerDomainException.class, () -> customer.replaceDetails(
                "Changed", null, null, "invalid", null, null, List.of(), List.of()));
        assertEquals("Acme", customer.getName());
        assertEquals("contact@example.invalid", customer.getEmail());
        assertThrows(CustomerDomainException.class, () -> customer.changeParent(new CustomerId(21L)));
        assertThrows(CustomerDomainException.class, () -> customer.replaceCollaborators(List.of(
                new CustomerCollaborator(OWNER, CustomerCollaboratorAccess.READ))));
    }

    @Test
    void dealStateTracksDealTimeAndDeletedCustomersAreImmutable() {
        Customer customer = customer();

        customer.changeState(true, CustomerDealStatus.WON);
        assertTrue(customer.isLocked());
        assertEquals(CustomerDealStatus.WON, customer.getDealStatus());
        assertTrue(customer.getDealTime() != null);

        customer.changeState(false, CustomerDealStatus.OPEN);
        assertFalse(customer.isLocked());
        assertNull(customer.getDealTime());

        customer.delete();
        assertTrue(customer.isDeleted());
        assertThrows(CustomerDomainException.class, () -> customer.replaceTags(List.of("late")));
        assertThrows(CustomerDomainException.class, () -> customer.changeOwner(new MemberId(13L)));
    }

    @Test
    void exitingOnlyMutatesAnExistingCollaboration() {
        Customer customer = customer();
        MemberId collaborator = new MemberId(12L);
        customer.replaceCollaborators(List.of(new CustomerCollaborator(
                collaborator, CustomerCollaboratorAccess.READ)));
        var updatedTime = customer.getUpdatedTime();

        assertFalse(customer.exitCollaborator(new MemberId(13L)));
        assertEquals(updatedTime, customer.getUpdatedTime());
        assertEquals(List.of(collaborator), customer.getCollaborators().stream()
                .map(CustomerCollaborator::memberId).toList());

        assertTrue(customer.exitCollaborator(collaborator));
        assertTrue(customer.getCollaborators().isEmpty());
    }

    @Test
    void batchTagChangesReuseTheCanonicalNfkcKey() {
        Customer customer = customer();
        customer.replaceTags(List.of("ﬃ", "VIP", "Ａ"));

        customer.changeTags(List.of("vip", "a"), List.of("ffi"));

        assertEquals(List.of("VIP", "Ａ"), customer.getTags());
    }

    @Test
    void fieldPolicyNormalizesTypesAndRejectsInvalidOrRequiredEmptyValues() {
        FieldDefinition amount = customField(
                "cf_amount", CustomFieldType.DECIMAL, false, false, List.of());
        FieldDefinition requiredEmail = customField(
                "cf_email", CustomFieldType.EMAIL, true, true, List.of());
        FieldDefinition priority = customField(
                "cf_priority", CustomFieldType.SELECT, false, false,
                List.of(new FieldOption("high", "High", 1)));

        assertEquals(List.of("120.5"),
                CustomerFieldValuePolicy.normalize(amount, List.of("120.500")).values());
        assertEquals(List.of("person@example.invalid"), CustomerFieldValuePolicy.normalize(
                requiredEmail, List.of("Person@Example.Invalid")).values());
        assertThrows(CustomerDomainException.class,
                () -> CustomerFieldValuePolicy.normalize(requiredEmail, List.of()));
        assertThrows(CustomerDomainException.class,
                () -> CustomerFieldValuePolicy.normalize(priority, List.of("unknown")));
    }

    @Test
    void websiteValidatesTheFinalAsciiPersistenceRepresentation() {
        Customer customer = customer();
        String expandedWebsite = "https://example.invalid/" + "你".repeat(476);
        assertEquals(500, expandedWebsite.length());

        assertThrows(CustomerDomainException.class, () -> customer.replaceDetails(
                customer.getName(), customer.getPhoneNumber(), customer.getTelephone(),
                customer.getEmail(), expandedWebsite, customer.getDescription(),
                customer.getAddresses(), customer.getCustomFieldValues()));
        assertEquals("https://example.invalid", customer.getWebsite());
    }

    private Customer customer() {
        return Customer.create(TENANT, OWNER, " Acme ", "+8613800138000", null,
                "Contact@Example.Invalid", "https://example.invalid", null,
                List.of(new CustomerAddress("HQ", "Headquarters", "CN", "Shanghai",
                        "Shanghai", "200000", "Example road 1",
                        new BigDecimal("121.4737"), new BigDecimal("31.2304"), true)),
                List.of());
    }

    private FieldDefinition customField(
            String code, CustomFieldType type, boolean required, boolean unique,
            List<FieldOption> options) {
        return FieldDefinition.createCustom(TENANT, BusinessObjectType.CUSTOMER,
                new FieldCode(code), code, null, type, required, unique, true,
                1, null, options);
    }
}
