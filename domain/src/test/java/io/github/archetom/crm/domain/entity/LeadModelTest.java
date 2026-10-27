package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.LeadDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.LeadCollaboratorAccess;
import io.github.archetom.crm.domain.model.LeadDealStatus;
import io.github.archetom.crm.domain.policy.LeadFieldValuePolicy;
import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeadModelTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId OWNER = new MemberId(11L);

    @Test
    void aggregateNormalizesBoundedValuesAndKeepsFailedChangesAtomic() {
        Lead lead = lead();
        lead.onPersisted(new LeadId(21L), 0L, null, null);
        lead.replaceTags(List.of(
                " Priority ", "priority", "Enterprise", "e", "E", "é"));
        lead.replaceCollaborators(List.of(new LeadCollaborator(
                new MemberId(12L), LeadCollaboratorAccess.WRITE)));

        assertEquals(List.of("Priority", "Enterprise", "e", "é"), lead.getTags());
        assertEquals("hq", lead.getAddresses().getFirst().key());
        assertEquals("lead@example.invalid", lead.getEmail());
        assertThrows(LeadDomainException.class, () -> lead.replaceDetails(
                "Changed", null, null, "invalid", null, null, List.of(), List.of()));
        assertEquals("Acme", lead.getName());
        assertEquals("lead@example.invalid", lead.getEmail());
        assertThrows(LeadDomainException.class, () -> lead.changeParent(new LeadId(21L)));
        assertThrows(LeadDomainException.class, () -> lead.replaceCollaborators(List.of(
                new LeadCollaborator(OWNER, LeadCollaboratorAccess.READ))));
    }

    @Test
    void dealStateTracksDealTimeAndDeletedLeadsAreImmutable() {
        Lead lead = lead();

        lead.changeState(true, LeadDealStatus.WON);
        assertTrue(lead.isLocked());
        assertEquals(LeadDealStatus.WON, lead.getDealStatus());
        assertTrue(lead.getDealTime() != null);

        lead.changeState(false, LeadDealStatus.OPEN);
        assertFalse(lead.isLocked());
        assertNull(lead.getDealTime());

        lead.delete();
        assertTrue(lead.isDeleted());
        assertThrows(LeadDomainException.class, () -> lead.replaceTags(List.of("late")));
        assertThrows(LeadDomainException.class, () -> lead.changeOwner(new MemberId(13L)));
    }

    @Test
    void conversionAssociatesBothPersistedProjectionsAndFreezesTheLead() {
        Lead lead = lead();
        CustomerId customerId = new CustomerId(31L);
        ContactId contactId = new ContactId(41L);

        lead.convert(customerId, contactId);

        assertEquals(customerId, lead.getCustomerId());
        assertEquals(contactId, lead.getConvertedContactId());
        assertEquals("CONVERTED", lead.getStatus().name());
        assertThrows(LeadDomainException.class,
                () -> lead.convert(new CustomerId(32L), new ContactId(42L)));
    }

    @Test
    void conversionPreservesAnExistingWonDealTime() {
        Lead lead = lead();
        lead.changeState(false, LeadDealStatus.WON);
        LocalDateTime wonAt = lead.getDealTime();

        lead.convert(new CustomerId(31L), new ContactId(41L));

        assertEquals(wonAt, lead.getDealTime());
    }

    @Test
    void exitingOnlyMutatesAnExistingCollaboration() {
        Lead lead = lead();
        MemberId collaborator = new MemberId(12L);
        lead.replaceCollaborators(List.of(new LeadCollaborator(
                collaborator, LeadCollaboratorAccess.READ)));
        var updatedTime = lead.getUpdatedTime();

        assertFalse(lead.exitCollaborator(new MemberId(13L)));
        assertEquals(updatedTime, lead.getUpdatedTime());
        assertEquals(List.of(collaborator), lead.getCollaborators().stream()
                .map(LeadCollaborator::memberId).toList());

        assertTrue(lead.exitCollaborator(collaborator));
        assertTrue(lead.getCollaborators().isEmpty());
    }

    @Test
    void batchTagChangesReuseTheCanonicalNfkcKey() {
        Lead lead = lead();
        lead.replaceTags(List.of("ﬃ", "VIP", "Ａ"));

        lead.changeTags(List.of("vip", "a"), List.of("ffi"));

        assertEquals(List.of("VIP", "Ａ"), lead.getTags());
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
                LeadFieldValuePolicy.normalize(amount, List.of("120.500")).values());
        assertEquals(List.of("person@example.invalid"), LeadFieldValuePolicy.normalize(
                requiredEmail, List.of("Person@Example.Invalid")).values());
        assertThrows(LeadDomainException.class,
                () -> LeadFieldValuePolicy.normalize(requiredEmail, List.of()));
        assertThrows(LeadDomainException.class,
                () -> LeadFieldValuePolicy.normalize(priority, List.of("unknown")));
    }

    @Test
    void websiteValidatesTheFinalAsciiPersistenceRepresentation() {
        Lead lead = lead();
        String expandedWebsite = "https://example.invalid/" + "你".repeat(476);
        assertEquals(500, expandedWebsite.length());

        assertThrows(LeadDomainException.class, () -> lead.replaceDetails(
                lead.getName(), lead.getPhoneNumber(), lead.getTelephone(),
                lead.getEmail(), expandedWebsite, lead.getDescription(),
                lead.getAddresses(), lead.getCustomFieldValues()));
        assertEquals("https://example.invalid", lead.getWebsite());
    }

    private Lead lead() {
        return Lead.create(TENANT, OWNER, " Acme ", "+8613800138000", null,
                "Lead@Example.Invalid", "https://example.invalid", null,
                List.of(new LeadAddress("HQ", "Headquarters", "CN", "Shanghai",
                        "Shanghai", "200000", "Example road 1",
                        new BigDecimal("121.4737"), new BigDecimal("31.2304"), true)),
                List.of());
    }

    private FieldDefinition customField(
            String code, CustomFieldType type, boolean required, boolean unique,
            List<FieldOption> options) {
        return FieldDefinition.createCustom(TENANT, BusinessObjectType.LEAD,
                new FieldCode(code), code, null, type, required, unique, true,
                1, null, options);
    }
}
