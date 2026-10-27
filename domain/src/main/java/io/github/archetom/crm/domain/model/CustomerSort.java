package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.CustomerDomainException;

/**
 * Validated ordering for a customer page. Static columns are enum-owned; custom orders retain only
 * a server-resolved definition ID and scalar type, never a caller-provided SQL name or expression.
 */
public record CustomerSort(
        CustomerSortField field,
        CustomerSortDirection direction,
        Long customFieldDefinitionId,
        CustomFieldType customFieldType) {

    private static final CustomerSort DEFAULT = new CustomerSort(
            CustomerSortField.UPDATED_TIME, CustomerSortDirection.DESC, null, null);

    public CustomerSort {
        boolean staticField = field != null;
        boolean customField = customFieldDefinitionId != null || customFieldType != null;
        if (direction == null || staticField == customField
                || customField && (customFieldDefinitionId == null || customFieldDefinitionId <= 0
                || customFieldType == null || !customFieldType.supportsListSorting())) {
            throw new CustomerDomainException("customer sort is invalid");
        }
    }

    public static CustomerSort from(String field, String direction) {
        if (isBlank(field) && isBlank(direction)) {
            return DEFAULT;
        }
        if (isBlank(field) || isBlank(direction)) {
            throw new CustomerDomainException("customer sort must include field and direction");
        }
        try {
            return new CustomerSort(CustomerSortField.valueOf(field),
                    CustomerSortDirection.valueOf(direction), null, null);
        } catch (IllegalArgumentException exception) {
            throw new CustomerDomainException("customer sort is invalid");
        }
    }

    public static CustomerSort custom(
            Long fieldDefinitionId, CustomFieldType fieldType, String direction) {
        if (isBlank(direction)) {
            throw new CustomerDomainException("customer custom sort must include direction");
        }
        try {
            return new CustomerSort(null, CustomerSortDirection.valueOf(direction),
                    fieldDefinitionId, fieldType);
        } catch (IllegalArgumentException exception) {
            throw new CustomerDomainException("customer sort is invalid");
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
