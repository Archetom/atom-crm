package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.application.vo.ContactVO;
import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.ContactCollaborator;
import io.github.archetom.crm.domain.model.ContactCollaboratorAccess;
import io.github.archetom.crm.domain.model.ContactDealStatus;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContactAssemblerTest {

    @Test
    void dealTimeRequiresUnmaskedDealStatusAccess() {
        Contact contact = Contact.create(new TenantId(9L), new MemberId(11L),
                "Acme", null, null, null, null, null, List.of(), List.of());
        contact.changeState(false, ContactDealStatus.WON);
        contact.onPersisted(new ContactId(21L), 0L,
                LocalDateTime.now(), LocalDateTime.now());
        FieldCode dealStatus = new FieldCode(ContactFieldCatalog.DEAL_STATUS);

        ContactVO masked = ContactAssembler.toVO(contact, false, Map.of(
                dealStatus, new EffectiveFieldAccess(
                        FieldAccessLevel.READ, FieldMaskType.FULL)), Set.of());
        assertEquals("[REDACTED]", masked.dealStatus());
        assertNull(masked.dealTime());

        ContactVO unmasked = ContactAssembler.toVO(contact, false, Map.of(
                dealStatus, new EffectiveFieldAccess(
                        FieldAccessLevel.READ, FieldMaskType.NONE)), Set.of());
        assertEquals(ContactDealStatus.WON.name(), unmasked.dealStatus());
        assertNotNull(unmasked.dealTime());
    }

    @Test
    void auditTimesRequireUnmaskedFieldAccess() {
        Contact contact = Contact.create(new TenantId(9L), new MemberId(11L),
                "Acme", null, null, null, null, null, List.of(), List.of());
        LocalDateTime createdTime = LocalDateTime.of(2026, 7, 15, 9, 30);
        LocalDateTime updatedTime = createdTime.plusHours(1);
        contact.onPersisted(new ContactId(21L), 0L, createdTime, updatedTime);
        FieldCode created = new FieldCode(ContactFieldCatalog.CREATED_TIME);
        FieldCode updated = new FieldCode(ContactFieldCatalog.UPDATED_TIME);

        ContactVO masked = ContactAssembler.toVO(contact, false, Map.of(
                created, new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.FULL),
                updated, new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.PARTIAL)),
                Set.of());
        assertNull(masked.createdTime());
        assertNull(masked.updatedTime());

        ContactVO unmasked = ContactAssembler.toVO(contact, false, Map.of(
                created, new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.NONE),
                updated, new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.NONE)),
                Set.of());
        assertEquals(createdTime, unmasked.createdTime());
        assertEquals(updatedTime, unmasked.updatedTime());
    }

    @Test
    void partialMaskPreservesSupplementaryUnicodeCodePoints() {
        Contact contact = Contact.create(new TenantId(9L), new MemberId(11L),
                "😀Acme🚀", null, null, null, null, null, List.of(), List.of());
        contact.onPersisted(new ContactId(21L), 0L,
                LocalDateTime.now(), LocalDateTime.now());

        ContactVO masked = ContactAssembler.toVO(contact, false, Map.of(
                new FieldCode(ContactFieldCatalog.NAME), new EffectiveFieldAccess(
                        FieldAccessLevel.READ, FieldMaskType.PARTIAL)), Set.of());

        assertEquals("😀****🚀", masked.name());
    }

    @Test
    void collaboratorsRequireAnExplicitlyAuthorizedMappingPath() {
        Contact contact = Contact.create(new TenantId(9L), new MemberId(11L),
                "Acme", null, null, null, null, null, List.of(), List.of());
        contact.replaceCollaborators(List.of(new ContactCollaborator(
                new MemberId(12L), ContactCollaboratorAccess.WRITE)));
        contact.onPersisted(new ContactId(21L), 0L,
                LocalDateTime.now(), LocalDateTime.now());

        ContactVO ordinary = ContactAssembler.toVO(
                contact, false, Map.of(), Set.of());
        ContactVO authorized = ContactAssembler.toVO(
                contact, false, Map.of(), Set.of(), true);

        assertEquals(List.of(), ordinary.collaborators());
        assertEquals(12L, authorized.collaborators().getFirst().actorId());
        assertEquals("WRITE", authorized.collaborators().getFirst().access());
    }
}
