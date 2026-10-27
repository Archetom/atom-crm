package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ReceivableDomainException;

/**
 * Validated ordering for a receivable page. Static columns are enum-owned; a custom order keeps
 * only server-resolved field metadata and never a request-controlled SQL identifier.
 */
public record ReceivableSort(
        ReceivableSortField field,
        ReceivableSortDirection direction,
        Long customFieldDefinitionId,
        CustomFieldType customFieldType) {

    private static final ReceivableSort DEFAULT = new ReceivableSort(
            ReceivableSortField.RECEIVED_DATE, ReceivableSortDirection.DESC, null, null);

    public ReceivableSort {
        boolean staticField = field != null;
        boolean customField = customFieldDefinitionId != null || customFieldType != null;
        if (direction == null || staticField == customField
                || customField && (customFieldDefinitionId == null || customFieldDefinitionId <= 0
                || customFieldType == null || !customFieldType.supportsListSorting())) {
            throw new ReceivableDomainException("receivable sort is invalid");
        }
    }

    public static ReceivableSort from(String field, String direction) {
        if (isBlank(field) && isBlank(direction)) {
            return DEFAULT;
        }
        if (isBlank(field) || isBlank(direction)) {
            throw new ReceivableDomainException("receivable sort must include field and direction");
        }
        try {
            return new ReceivableSort(ReceivableSortField.valueOf(field),
                    ReceivableSortDirection.valueOf(direction), null, null);
        } catch (IllegalArgumentException exception) {
            throw new ReceivableDomainException("receivable sort is invalid");
        }
    }

    public static ReceivableSort custom(
            Long fieldDefinitionId, CustomFieldType fieldType, String direction) {
        if (isBlank(direction)) {
            throw new ReceivableDomainException("receivable custom sort must include direction");
        }
        try {
            return new ReceivableSort(null, ReceivableSortDirection.valueOf(direction),
                    fieldDefinitionId, fieldType);
        } catch (IllegalArgumentException exception) {
            throw new ReceivableDomainException("receivable sort is invalid");
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
