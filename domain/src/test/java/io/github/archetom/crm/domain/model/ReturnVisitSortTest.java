package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ReturnVisitDomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnVisitSortTest {

    @Test
    void customSortRetainsOnlyServerResolvedScalarMetadata() {
        ReturnVisitSort sort = ReturnVisitSort.custom(42L, CustomFieldType.INTEGER, "DESC");

        assertTrue(sort.isCustom());
        assertTrue(sort.isCustomNumeric());
        assertEquals(42L, sort.customFieldDefinitionId());
        assertEquals(ReturnVisitSortDirection.DESC, sort.direction());
    }

    @Test
    void rejectsMissingOrUnsupportedCustomSortMetadata() {
        assertThrows(ReturnVisitDomainException.class,
                () -> ReturnVisitSort.custom(42L, CustomFieldType.MULTI_SELECT, "ASC"));
        assertThrows(ReturnVisitDomainException.class,
                () -> ReturnVisitSort.custom(null, CustomFieldType.TEXT, "ASC"));

        ReturnVisitSort standard = ReturnVisitSort.from("VISIT_NUMBER", "DESC");
        assertFalse(standard.isCustom());
        assertFalse(standard.isCustomNumeric());
    }
}
