package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ContactDomainException;

/**
 * Validated ordering for a contact page. Static columns are enum-owned; custom orders retain only
 * a server-resolved definition ID and scalar type, never a caller-provided SQL name or expression.
 */
public record ContactSort(
        ContactSortField field,
        ContactSortDirection direction,
        Long customFieldDefinitionId,
        CustomFieldType customFieldType) {

    private static final ContactSort DEFAULT = new ContactSort(
            ContactSortField.UPDATED_TIME, ContactSortDirection.DESC, null, null);

    public ContactSort {
        boolean staticField = field != null;
        boolean customField = customFieldDefinitionId != null || customFieldType != null;
        if (direction == null || staticField == customField
                || customField && (customFieldDefinitionId == null || customFieldDefinitionId <= 0
                || customFieldType == null || !customFieldType.supportsListSorting())) {
            throw new ContactDomainException("contact sort is invalid");
        }
    }

    public static ContactSort from(String field, String direction) {
        if (isBlank(field) && isBlank(direction)) {
            return DEFAULT;
        }
        if (isBlank(field) || isBlank(direction)) {
            throw new ContactDomainException("contact sort must include field and direction");
        }
        try {
            return new ContactSort(ContactSortField.valueOf(field), ContactSortDirection.valueOf(direction),
                    null, null);
        } catch (IllegalArgumentException exception) {
            throw new ContactDomainException("contact sort is invalid");
        }
    }

    public static ContactSort custom(
            Long fieldDefinitionId, CustomFieldType fieldType, String direction) {
        if (isBlank(direction)) {
            throw new ContactDomainException("contact custom sort must include direction");
        }
        try {
            return new ContactSort(null, ContactSortDirection.valueOf(direction),
                    fieldDefinitionId, fieldType);
        } catch (IllegalArgumentException exception) {
            throw new ContactDomainException("contact sort is invalid");
        }
    }

    public boolean isCustom() {
        return customFieldDefinitionId != null;
    }

    public boolean isCustomNumeric() {
        return isCustom() && customFieldType.isNumericScalar();
    }

    public boolean isDefault() {
        return equals(DEFAULT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
