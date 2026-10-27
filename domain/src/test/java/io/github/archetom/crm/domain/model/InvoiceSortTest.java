package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.InvoiceDomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceSortTest {

    @Test
    void customSortRetainsOnlyServerResolvedScalarMetadata() {
        InvoiceSort sort = InvoiceSort.custom(42L, CustomFieldType.DECIMAL, "ASC");

        assertTrue(sort.isCustom());
        assertTrue(sort.isCustomNumeric());
        assertEquals(42L, sort.customFieldDefinitionId());
        assertEquals(InvoiceSortDirection.ASC, sort.direction());
    }

    @Test
    void rejectsMissingOrUnsupportedCustomSortMetadata() {
        assertThrows(InvoiceDomainException.class,
                () -> InvoiceSort.custom(42L, CustomFieldType.MULTI_SELECT, "ASC"));
        assertThrows(InvoiceDomainException.class,
                () -> InvoiceSort.custom(null, CustomFieldType.TEXT, "ASC"));

        InvoiceSort standard = InvoiceSort.from("APPLICATION_NUMBER", "DESC");
        assertFalse(standard.isCustom());
        assertFalse(standard.isCustomNumeric());
    }
}
