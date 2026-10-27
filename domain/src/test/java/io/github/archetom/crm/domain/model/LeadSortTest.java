package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.LeadDomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeadSortTest {

    @Test
    void customSortRetainsOnlyServerResolvedScalarMetadata() {
        LeadSort sort = LeadSort.custom(42L, CustomFieldType.DECIMAL, "ASC");

        assertTrue(sort.isCustom());
        assertTrue(sort.isCustomNumeric());
        assertEquals(42L, sort.customFieldDefinitionId());
        assertEquals(LeadSortDirection.ASC, sort.direction());
    }

    @Test
    void rejectsMissingOrUnsupportedCustomSortMetadata() {
        assertThrows(LeadDomainException.class,
                () -> LeadSort.custom(42L, CustomFieldType.MULTI_SELECT, "ASC"));
        assertThrows(LeadDomainException.class,
                () -> LeadSort.custom(null, CustomFieldType.TEXT, "ASC"));

        LeadSort standard = LeadSort.from("NAME", "DESC");
        assertFalse(standard.isCustom());
        assertFalse(standard.isCustomNumeric());
    }
}
