package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ContractDomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractSortTest {

    @Test
    void customSortRetainsOnlyServerResolvedScalarMetadata() {
        ContractSort sort = ContractSort.custom(42L, CustomFieldType.DECIMAL, "ASC");

        assertTrue(sort.isCustom());
        assertTrue(sort.isCustomNumeric());
        assertEquals(42L, sort.customFieldDefinitionId());
        assertEquals(ContractSortDirection.ASC, sort.direction());
    }

    @Test
    void rejectsMissingOrUnsupportedCustomSortMetadata() {
        assertThrows(ContractDomainException.class,
                () -> ContractSort.custom(42L, CustomFieldType.MULTI_SELECT, "ASC"));
        assertThrows(ContractDomainException.class,
                () -> ContractSort.custom(null, CustomFieldType.TEXT, "ASC"));

        ContractSort standard = ContractSort.from("NAME", "DESC");
        assertFalse(standard.isCustom());
        assertFalse(standard.isCustomNumeric());
    }
}
