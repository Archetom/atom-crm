package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ProductDomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductSortTest {

    @Test
    void customSortRetainsOnlyServerResolvedScalarMetadata() {
        ProductSort sort = ProductSort.custom(42L, CustomFieldType.DECIMAL, "ASC");

        assertTrue(sort.isCustom());
        assertTrue(sort.isCustomNumeric());
        assertEquals(42L, sort.customFieldDefinitionId());
        assertEquals(ProductSortDirection.ASC, sort.direction());
    }

    @Test
    void rejectsMissingOrUnsupportedCustomSortMetadata() {
        assertThrows(ProductDomainException.class,
                () -> ProductSort.custom(42L, CustomFieldType.MULTI_SELECT, "ASC"));
        assertThrows(ProductDomainException.class,
                () -> ProductSort.custom(null, CustomFieldType.TEXT, "ASC"));

        ProductSort standard = ProductSort.from("NAME", "DESC");
        assertFalse(standard.isCustom());
        assertFalse(standard.isCustomNumeric());
    }
}
