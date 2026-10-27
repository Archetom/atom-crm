package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.OpportunityDomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpportunitySortTest {

    @Test
    void customSortRetainsOnlyServerResolvedScalarMetadata() {
        OpportunitySort sort = OpportunitySort.custom(42L, CustomFieldType.DECIMAL, "ASC");

        assertTrue(sort.isCustom());
        assertTrue(sort.isCustomNumeric());
        assertEquals(42L, sort.customFieldDefinitionId());
        assertEquals(OpportunitySortDirection.ASC, sort.direction());
    }

    @Test
    void rejectsMissingOrUnsupportedCustomSortMetadata() {
        assertThrows(OpportunityDomainException.class,
                () -> OpportunitySort.custom(42L, CustomFieldType.MULTI_SELECT, "ASC"));
        assertThrows(OpportunityDomainException.class,
                () -> OpportunitySort.custom(null, CustomFieldType.TEXT, "ASC"));

        OpportunitySort standard = OpportunitySort.from("NAME", "DESC");
        assertFalse(standard.isCustom());
        assertFalse(standard.isCustomNumeric());
    }
}
