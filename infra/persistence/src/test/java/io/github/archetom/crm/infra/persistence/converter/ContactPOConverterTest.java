package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.ContactAddress;
import io.github.archetom.crm.domain.entity.ContactCollaborator;
import io.github.archetom.crm.domain.entity.ContactFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.ContactCollaboratorAccess;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactAddressPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactCollaboratorPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactTagPO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ContactPOConverterTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final ContactId CONTACT_ID = new ContactId(21L);
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 7, 15, 12, 0);

    @Test
    void contactAndOwnedValuesRoundTripWithoutEvents() {
        ObjectMapper objectMapper = new ObjectMapper();
        ContactPOConverter converter = new ContactPOConverter(objectMapper);
        FieldDefinition field = FieldDefinition.createCustom(TENANT, BusinessObjectType.CONTACT,
                new FieldCode("cf_priority"), "Priority", null, CustomFieldType.TEXT,
                false, false, true, 1, null, List.of());
        field.onPersisted(new FieldDefinitionId(31L), 0L, CREATED, CREATED);

        Contact contact = Contact.create(TENANT, new MemberId(11L), "Acme",
                "+8613800138000", "021-12345678", "contact@example.invalid",
                "https://example.invalid", "Important account",
                List.of(new ContactAddress("hq", "Headquarters", "CN", "Shanghai",
                        "Shanghai", "200000", "Example road 1",
                        new BigDecimal("121.4737"), new BigDecimal("31.2304"), true)),
                List.of(new ContactFieldValue(field.getCode(), List.of("high"))));
        contact.replaceTags(List.of("Enterprise"));
        contact.replaceCollaborators(List.of(new ContactCollaborator(
                new MemberId(12L), ContactCollaboratorAccess.WRITE)));
        contact.onPersisted(CONTACT_ID, 4L, CREATED, CREATED.plusHours(1));

        ContactPO root = converter.toPO(contact);
        ContactAddressPO address = converter.toPO(TENANT, CONTACT_ID,
                contact.getAddresses().getFirst());
        ContactTagPO tag = converter.toPO(TENANT, CONTACT_ID, contact.getTags().getFirst());
        ContactCollaboratorPO collaborator = converter.toPO(TENANT, CONTACT_ID,
                contact.getCollaborators().getFirst());
        ContactFieldValuePO fieldValue = converter.toPO(TENANT, CONTACT_ID,
                contact.getCustomFieldValues().getFirst(), field);

        Contact restored = converter.toDomain(root, List.of(address), List.of(tag),
                List.of(collaborator), List.of(fieldValue), Map.of(31L, field));

        assertEquals(CONTACT_ID, restored.getId());
        assertEquals(4L, restored.getVersion());
        assertEquals(contact.getAddresses(), restored.getAddresses());
        assertEquals(contact.getTags(), restored.getTags());
        assertEquals("enterprise", tag.getTagKey());
        assertEquals(contact.getCollaborators(), restored.getCollaborators());
        assertEquals(contact.getCustomFieldValues(), restored.getCustomFieldValues());
        assertFalse(restored.hasDomainEvents());
    }
}
