package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.InvoiceDomainException;

/** Validated ordering for an invoice page; column names never originate from request input. */
public record InvoiceSort(
        InvoiceSortField field,
        InvoiceSortDirection direction,
        Long customFieldDefinitionId,
        CustomFieldType customFieldType) {

    private static final InvoiceSort DEFAULT = new InvoiceSort(
            InvoiceSortField.UPDATED_TIME, InvoiceSortDirection.DESC, null, null);

    public InvoiceSort {
        boolean staticField = field != null;
        boolean customField = customFieldDefinitionId != null || customFieldType != null;
        if (direction == null || staticField == customField
                || customField && (customFieldDefinitionId == null || customFieldDefinitionId <= 0
                || customFieldType == null || !customFieldType.supportsListSorting())) {
            throw new InvoiceDomainException("invoice sort is invalid");
        }
    }

    public static InvoiceSort from(String field, String direction) {
        if (isBlank(field) && isBlank(direction)) {
            return DEFAULT;
        }
        if (isBlank(field) || isBlank(direction)) {
            throw new InvoiceDomainException("invoice sort must include field and direction");
        }
        try {
            return new InvoiceSort(InvoiceSortField.valueOf(field),
                    InvoiceSortDirection.valueOf(direction), null, null);
        } catch (IllegalArgumentException exception) {
            throw new InvoiceDomainException("invoice sort is invalid");
        }
    }

    public static InvoiceSort custom(
            Long fieldDefinitionId, CustomFieldType fieldType, String direction) {
        if (isBlank(direction)) {
            throw new InvoiceDomainException("invoice custom sort must include direction");
        }
        try {
            return new InvoiceSort(null, InvoiceSortDirection.valueOf(direction),
                    fieldDefinitionId, fieldType);
        } catch (IllegalArgumentException exception) {
            throw new InvoiceDomainException("invoice sort is invalid");
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
