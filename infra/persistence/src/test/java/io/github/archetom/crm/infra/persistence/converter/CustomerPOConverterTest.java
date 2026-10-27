package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.CustomerAddress;
import io.github.archetom.crm.domain.entity.CustomerCollaborator;
import io.github.archetom.crm.domain.entity.CustomerFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.CustomerCollaboratorAccess;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerAddressPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerCollaboratorPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerTagPO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CustomerPOConverterTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final CustomerId CUSTOMER_ID = new CustomerId(21L);
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 7, 15, 12, 0);

    @Test
    void customerAndOwnedValuesRoundTripWithoutEvents() {
        ObjectMapper objectMapper = new ObjectMapper();
        CustomerPOConverter converter = new CustomerPOConverter(objectMapper);
        FieldDefinition field = FieldDefinition.createCustom(TENANT, BusinessObjectType.CUSTOMER,
                new FieldCode("cf_priority"), "Priority", null, CustomFieldType.TEXT,
                false, false, true, 1, null, List.of());
        field.onPersisted(new FieldDefinitionId(31L), 0L, CREATED, CREATED);

        Customer customer = Customer.create(TENANT, new MemberId(11L), "Acme",
                "+8613800138000", "021-12345678", "contact@example.invalid",
                "https://example.invalid", "Important account",
                List.of(new CustomerAddress("hq", "Headquarters", "CN", "Shanghai",
                        "Shanghai", "200000", "Example road 1",
                        new BigDecimal("121.4737"), new BigDecimal("31.2304"), true)),
                List.of(new CustomerFieldValue(field.getCode(), List.of("high"))));
        customer.replaceTags(List.of("Enterprise"));
        customer.replaceCollaborators(List.of(new CustomerCollaborator(
                new MemberId(12L), CustomerCollaboratorAccess.WRITE)));
        customer.onPersisted(CUSTOMER_ID, 4L, CREATED, CREATED.plusHours(1));

        CustomerPO root = converter.toPO(customer);
        CustomerAddressPO address = converter.toPO(TENANT, CUSTOMER_ID,
                customer.getAddresses().getFirst());
        CustomerTagPO tag = converter.toPO(TENANT, CUSTOMER_ID, customer.getTags().getFirst());
        CustomerCollaboratorPO collaborator = converter.toPO(TENANT, CUSTOMER_ID,
                customer.getCollaborators().getFirst());
        CustomerFieldValuePO fieldValue = converter.toPO(TENANT, CUSTOMER_ID,
                customer.getCustomFieldValues().getFirst(), field);

        Customer restored = converter.toDomain(root, List.of(address), List.of(tag),
                List.of(collaborator), List.of(fieldValue), Map.of(31L, field));

        assertEquals(CUSTOMER_ID, restored.getId());
        assertEquals(4L, restored.getVersion());
        assertEquals(customer.getAddresses(), restored.getAddresses());
        assertEquals(customer.getTags(), restored.getTags());
        assertEquals("enterprise", tag.getTagKey());
        assertEquals(customer.getCollaborators(), restored.getCollaborators());
        assertEquals(customer.getCustomFieldValues(), restored.getCustomFieldValues());
        assertFalse(restored.hasDomainEvents());
    }
}
