package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ReceivableDomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceivableSortTest {

    @Test
    void customSortRetainsOnlyServerResolvedScalarMetadata() {
        ReceivableSort sort = ReceivableSort.custom(42L, CustomFieldType.DECIMAL, "ASC");

        assertTrue(sort.isCustom());
        assertTrue(sort.isCustomNumeric());
        assertEquals(42L, sort.customFieldDefinitionId());
        assertEquals(ReceivableSortDirection.ASC, sort.direction());
    }

    @Test
    void rejectsMissingOrUnsupportedCustomSortMetadata() {
        assertThrows(ReceivableDomainException.class,
                () -> ReceivableSort.custom(42L, CustomFieldType.MULTI_SELECT, "ASC"));
        assertThrows(ReceivableDomainException.class,
                () -> ReceivableSort.custom(null, CustomFieldType.TEXT, "ASC"));

        ReceivableSort standard = ReceivableSort.from("RECEIPT_NUMBER", "DESC");
        assertFalse(standard.isCustom());
        assertFalse(standard.isCustomNumeric());
    }
}
