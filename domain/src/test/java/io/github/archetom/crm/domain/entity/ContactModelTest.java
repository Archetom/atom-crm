package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ContactDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.ContactCollaboratorAccess;
import io.github.archetom.crm.domain.model.ContactDealStatus;
import io.github.archetom.crm.domain.policy.ContactFieldValuePolicy;
import io.github.archetom.crm.domain.valueobject.ContactId;
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

class ContactModelTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId OWNER = new MemberId(11L);

    @Test
    void aggregateNormalizesBoundedValuesAndKeepsFailedChangesAtomic() {
        Contact contact = contact();
        contact.onPersisted(new ContactId(21L), 0L, null, null);
        contact.replaceTags(List.of(
                " Priority ", "priority", "Enterprise", "e", "E", "é"));
        contact.replaceCollaborators(List.of(new ContactCollaborator(
                new MemberId(12L), ContactCollaboratorAccess.WRITE)));

        assertEquals(List.of("Priority", "Enterprise", "e", "é"), contact.getTags());
        assertEquals("hq", contact.getAddresses().getFirst().key());
        assertEquals("contact@example.invalid", contact.getEmail());
        assertThrows(ContactDomainException.class, () -> contact.replaceDetails(
                "Changed", null, null, "invalid", null, null, List.of(), List.of()));
        assertEquals("Acme", contact.getName());
        assertEquals("contact@example.invalid", contact.getEmail());
        assertThrows(ContactDomainException.class, () -> contact.changeParent(new ContactId(21L)));
        assertThrows(ContactDomainException.class, () -> contact.replaceCollaborators(List.of(
                new ContactCollaborator(OWNER, ContactCollaboratorAccess.READ))));
    }

    @Test
    void dealStateTracksDealTimeAndDeletedContactsAreImmutable() {
        Contact contact = contact();

        contact.changeState(true, ContactDealStatus.WON);
        assertTrue(contact.isLocked());
        assertEquals(ContactDealStatus.WON, contact.getDealStatus());
        assertTrue(contact.getDealTime() != null);

        contact.changeState(false, ContactDealStatus.OPEN);
        assertFalse(contact.isLocked());
        assertNull(contact.getDealTime());

        contact.delete();
        assertTrue(contact.isDeleted());
        assertThrows(ContactDomainException.class, () -> contact.replaceTags(List.of("late")));
        assertThrows(ContactDomainException.class, () -> contact.changeOwner(new MemberId(13L)));
    }

    @Test
    void exitingOnlyMutatesAnExistingCollaboration() {
        Contact contact = contact();
        MemberId collaborator = new MemberId(12L);
        contact.replaceCollaborators(List.of(new ContactCollaborator(
                collaborator, ContactCollaboratorAccess.READ)));
        var updatedTime = contact.getUpdatedTime();

        assertFalse(contact.exitCollaborator(new MemberId(13L)));
        assertEquals(updatedTime, contact.getUpdatedTime());
        assertEquals(List.of(collaborator), contact.getCollaborators().stream()
                .map(ContactCollaborator::memberId).toList());

        assertTrue(contact.exitCollaborator(collaborator));
        assertTrue(contact.getCollaborators().isEmpty());
    }

    @Test
    void batchTagChangesReuseTheCanonicalNfkcKey() {
        Contact contact = contact();
        contact.replaceTags(List.of("ﬃ", "VIP", "Ａ"));

        contact.changeTags(List.of("vip", "a"), List.of("ffi"));

        assertEquals(List.of("VIP", "Ａ"), contact.getTags());
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
                ContactFieldValuePolicy.normalize(amount, List.of("120.500")).values());
        assertEquals(List.of("person@example.invalid"), ContactFieldValuePolicy.normalize(
                requiredEmail, List.of("Person@Example.Invalid")).values());
        assertThrows(ContactDomainException.class,
                () -> ContactFieldValuePolicy.normalize(requiredEmail, List.of()));
        assertThrows(ContactDomainException.class,
                () -> ContactFieldValuePolicy.normalize(priority, List.of("unknown")));
    }

    @Test
    void websiteValidatesTheFinalAsciiPersistenceRepresentation() {
        Contact contact = contact();
        String expandedWebsite = "https://example.invalid/" + "你".repeat(476);
        assertEquals(500, expandedWebsite.length());

        assertThrows(ContactDomainException.class, () -> contact.replaceDetails(
                contact.getName(), contact.getPhoneNumber(), contact.getTelephone(),
                contact.getEmail(), expandedWebsite, contact.getDescription(),
                contact.getAddresses(), contact.getCustomFieldValues()));
        assertEquals("https://example.invalid", contact.getWebsite());
    }

    private Contact contact() {
        return Contact.create(TENANT, OWNER, " Acme ", "+8613800138000", null,
                "Contact@Example.Invalid", "https://example.invalid", null,
                List.of(new ContactAddress("HQ", "Headquarters", "CN", "Shanghai",
                        "Shanghai", "200000", "Example road 1",
                        new BigDecimal("121.4737"), new BigDecimal("31.2304"), true)),
                List.of());
    }

    private FieldDefinition customField(
            String code, CustomFieldType type, boolean required, boolean unique,
            List<FieldOption> options) {
        return FieldDefinition.createCustom(TENANT, BusinessObjectType.CONTACT,
                new FieldCode(code), code, null, type, required, unique, true,
                1, null, options);
    }
}
