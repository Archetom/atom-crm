package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.exception.ContactDomainException;
import io.github.archetom.crm.domain.exception.CustomerDomainException;
import io.github.archetom.crm.domain.exception.LeadDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static java.time.Duration.ofSeconds;

/**
 * A scientific-notation literal carries a negative scale, so a precision/scale pair alone does not
 * bound the expansion {@code BigDecimal.toPlainString()} performs. {@code 1E+2000000000} is
 * thirteen characters with {@code precision=1, scale=-2000000000}; expanding it allocates two
 * billion characters before any output-length check can reject it.
 */
class FieldValuePolicyDecimalBoundTest {

    private static final TenantId TENANT = new TenantId(1L);

    private static FieldDefinition decimalField(BusinessObjectType objectType) {
        return FieldDefinition.createCustom(TENANT, objectType, new FieldCode("cf_amount"),
                "Amount", null, CustomFieldType.DECIMAL, false, false, false, 0, null, List.of());
    }

    @Test
    void customerRejectsExpandingDecimalWithoutAllocatingIt() {
        FieldDefinition field = decimalField(BusinessObjectType.CUSTOMER);
        assertTimeoutPreemptively(ofSeconds(5), () -> {
            assertThrows(CustomerDomainException.class, () ->
                    CustomerFieldValuePolicy.normalize(field, List.of("1E+2000000000")));
            assertThrows(CustomerDomainException.class, () ->
                    CustomerFieldValuePolicy.normalize(field, List.of("-1E+1000000000")));
            assertThrows(CustomerDomainException.class, () ->
                    CustomerFieldValuePolicy.normalize(field, List.of("1E+40")));
        });
    }

    @Test
    void contactAndLeadShareTheSameDecimalBound() {
        assertTimeoutPreemptively(ofSeconds(5), () -> {
            assertThrows(ContactDomainException.class, () ->
                    ContactFieldValuePolicy.normalize(
                            decimalField(BusinessObjectType.CONTACT), List.of("1E+2000000000")));
            assertThrows(LeadDomainException.class, () ->
                    LeadFieldValuePolicy.normalize(
                            decimalField(BusinessObjectType.LEAD), List.of("1E+2000000000")));
        });
    }

    @Test
    void ordinaryDecimalsStillNormalize() {
        assertEquals(List.of("1234.56"), CustomerFieldValuePolicy
                .normalize(decimalField(BusinessObjectType.CUSTOMER), List.of("1234.5600"))
                .values());
        assertEquals(List.of("1234.56"), ContactFieldValuePolicy
                .normalize(decimalField(BusinessObjectType.CONTACT), List.of("1234.5600"))
                .values());
        assertEquals(List.of("1234.56"), LeadFieldValuePolicy
                .normalize(decimalField(BusinessObjectType.LEAD), List.of("1234.5600"))
                .values());
    }
}
