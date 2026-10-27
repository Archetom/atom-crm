package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ContactDomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactSortTest {

    @Test
    void customSortRetainsOnlyServerResolvedScalarMetadata() {
        ContactSort sort = ContactSort.custom(42L, CustomFieldType.DECIMAL, "ASC");

        assertTrue(sort.isCustom());
        assertTrue(sort.isCustomNumeric());
        assertEquals(42L, sort.customFieldDefinitionId());
        assertEquals(ContactSortDirection.ASC, sort.direction());
    }

    @Test
    void rejectsMissingOrUnsupportedCustomSortMetadata() {
        assertThrows(ContactDomainException.class,
                () -> ContactSort.custom(42L, CustomFieldType.MULTI_SELECT, "ASC"));
        assertThrows(ContactDomainException.class,
                () -> ContactSort.custom(null, CustomFieldType.TEXT, "ASC"));

        ContactSort standard = ContactSort.from("NAME", "DESC");
        assertFalse(standard.isCustom());
        assertFalse(standard.isCustomNumeric());
    }
}
