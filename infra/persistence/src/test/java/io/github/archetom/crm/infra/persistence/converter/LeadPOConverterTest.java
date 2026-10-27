package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.Lead;
import io.github.archetom.crm.domain.entity.LeadAddress;
import io.github.archetom.crm.domain.entity.LeadCollaborator;
import io.github.archetom.crm.domain.entity.LeadFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.LeadCollaboratorAccess;
import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadAddressPO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadCollaboratorPO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadPO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadTagPO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LeadPOConverterTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final LeadId LEAD_ID = new LeadId(21L);
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 7, 15, 12, 0);

    @Test
    void leadAndOwnedValuesRoundTripWithoutEvents() {
        ObjectMapper objectMapper = new ObjectMapper();
        LeadPOConverter converter = new LeadPOConverter(objectMapper);
        FieldDefinition field = FieldDefinition.createCustom(TENANT, BusinessObjectType.LEAD,
                new FieldCode("cf_priority"), "Priority", null, CustomFieldType.TEXT,
                false, false, true, 1, null, List.of());
        field.onPersisted(new FieldDefinitionId(31L), 0L, CREATED, CREATED);

        Lead lead = Lead.create(TENANT, new MemberId(11L), "Acme",
                "+8613800138000", "021-12345678", "lead@example.invalid",
                "https://example.invalid", "Important account",
                List.of(new LeadAddress("hq", "Headquarters", "CN", "Shanghai",
                        "Shanghai", "200000", "Example road 1",
                        new BigDecimal("121.4737"), new BigDecimal("31.2304"), true)),
                List.of(new LeadFieldValue(field.getCode(), List.of("high"))));
        lead.replaceTags(List.of("Enterprise"));
        lead.replaceCollaborators(List.of(new LeadCollaborator(
                new MemberId(12L), LeadCollaboratorAccess.WRITE)));
        lead.convert(new CustomerId(41L), new ContactId(51L));
        lead.onPersisted(LEAD_ID, 4L, CREATED, CREATED.plusHours(1));

        LeadPO root = converter.toPO(lead);
        LeadAddressPO address = converter.toPO(TENANT, LEAD_ID,
                lead.getAddresses().getFirst());
        LeadTagPO tag = converter.toPO(TENANT, LEAD_ID, lead.getTags().getFirst());
        LeadCollaboratorPO collaborator = converter.toPO(TENANT, LEAD_ID,
                lead.getCollaborators().getFirst());
        LeadFieldValuePO fieldValue = converter.toPO(TENANT, LEAD_ID,
                lead.getCustomFieldValues().getFirst(), field);

        Lead restored = converter.toDomain(root, List.of(address), List.of(tag),
                List.of(collaborator), List.of(fieldValue), Map.of(31L, field));

        assertEquals(LEAD_ID, restored.getId());
        assertEquals(4L, restored.getVersion());
        assertEquals(lead.getAddresses(), restored.getAddresses());
        assertEquals(lead.getTags(), restored.getTags());
        assertEquals("enterprise", tag.getTagKey());
        assertEquals(lead.getCollaborators(), restored.getCollaborators());
        assertEquals(lead.getCustomFieldValues(), restored.getCustomFieldValues());
        assertEquals(new CustomerId(41L), restored.getCustomerId());
        assertEquals(new ContactId(51L), restored.getConvertedContactId());
        assertFalse(restored.hasDomainEvents());
    }
}
