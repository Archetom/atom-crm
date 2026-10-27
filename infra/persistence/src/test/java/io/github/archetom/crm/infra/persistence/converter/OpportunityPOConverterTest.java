package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Opportunity;
import io.github.archetom.crm.domain.entity.OpportunityCollaborator;
import io.github.archetom.crm.domain.entity.OpportunityFieldValue;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.OpportunityCollaboratorAccess;
import io.github.archetom.crm.domain.model.OpportunityOutcome;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityCollaboratorPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityContactPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityPO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpportunityPOConverterTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final OpportunityId OPPORTUNITY_ID = new OpportunityId(21L);
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 7, 17, 12, 0);

    @Test
    void opportunityAndOwnedValuesRoundTripWithoutEvents() {
        OpportunityPOConverter converter = new OpportunityPOConverter(new ObjectMapper());
        FieldDefinition field = FieldDefinition.createCustom(
                TENANT, BusinessObjectType.OPPORTUNITY,
                new FieldCode("cf_priority"), "Priority", null,
                CustomFieldType.TEXT, false, false, true, 1, null, List.of());
        field.onPersisted(new FieldDefinitionId(31L), 0L, CREATED, CREATED);

        ContactId primary = new ContactId(41L);
        Opportunity opportunity = Opportunity.create(
                TENANT, new MemberId(11L), new CustomerId(51L), "Renewal",
                "direct", "negotiation", LocalDate.of(2027, 1, 31),
                new BigDecimal("1250.50"), CREATED.plusDays(1), "Annual renewal",
                List.of(primary, new ContactId(42L)), primary,
                List.of(new OpportunityCollaborator(
                        new MemberId(12L), OpportunityCollaboratorAccess.WRITE)),
                List.of(new OpportunityFieldValue(
                        field.getCode(), List.of("high"))));
        opportunity.changeOutcome(OpportunityOutcome.WON, "Signed");
        opportunity.onPersisted(
                OPPORTUNITY_ID, 4L, CREATED, CREATED.plusHours(1));

        OpportunityPO root = converter.toPO(opportunity);
        List<OpportunityContactPO> contacts = opportunity.getContactIds().stream()
                .map(contact -> converter.toPO(
                        TENANT, OPPORTUNITY_ID, opportunity.getCustomerId(), contact, primary))
                .toList();
        OpportunityCollaboratorPO collaborator = converter.toPO(
                TENANT, OPPORTUNITY_ID, opportunity.getCollaborators().getFirst());
        OpportunityFieldValuePO fieldValue = converter.toPO(
                TENANT, OPPORTUNITY_ID,
                opportunity.getCustomFieldValues().getFirst(), field);

        Opportunity restored = converter.toDomain(
                root, contacts, List.of(collaborator), List.of(fieldValue),
                List.of(),
                Map.of(31L, field));

        assertEquals(OPPORTUNITY_ID, restored.getId());
        assertEquals(4L, restored.getVersion());
        assertEquals(opportunity.getCustomerId(), restored.getCustomerId());
        assertEquals(opportunity.getOwnerMemberId(), restored.getOwnerMemberId());
        assertEquals(opportunity.getContactIds(), restored.getContactIds());
        assertEquals(primary, restored.getPrimaryContactId());
        assertEquals(opportunity.getCollaborators(), restored.getCollaborators());
        assertEquals(opportunity.getCustomFieldValues(), restored.getCustomFieldValues());
        assertEquals(OpportunityOutcome.WON, restored.getOutcome());
        assertEquals("Signed", restored.getOutcomeRemark());
        assertEquals(new BigDecimal("1250.50"), restored.getEstimatedAmount());
        assertFalse(restored.hasDomainEvents());
    }
}
