package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ReturnVisitDomainException;

/**
 * Validated ordering for a return-visit page. Static columns are enum-owned; custom ordering
 * retains only trusted field metadata resolved by the application layer.
 */
public record ReturnVisitSort(
        ReturnVisitSortField field,
        ReturnVisitSortDirection direction,
        Long customFieldDefinitionId,
        CustomFieldType customFieldType) {

    private static final ReturnVisitSort DEFAULT = new ReturnVisitSort(
            ReturnVisitSortField.VISIT_TIME, ReturnVisitSortDirection.DESC, null, null);

    public ReturnVisitSort {
        boolean staticField = field != null;
        boolean customField = customFieldDefinitionId != null || customFieldType != null;
        if (direction == null || staticField == customField
                || customField && (customFieldDefinitionId == null || customFieldDefinitionId <= 0
                || customFieldType == null || !customFieldType.supportsListSorting())) {
            throw new ReturnVisitDomainException("return visit sort is invalid");
        }
    }

    public static ReturnVisitSort from(String field, String direction) {
        if (isBlank(field) && isBlank(direction)) {
            return DEFAULT;
        }
        if (isBlank(field) || isBlank(direction)) {
            throw new ReturnVisitDomainException("return visit sort must include field and direction");
        }
        try {
            return new ReturnVisitSort(ReturnVisitSortField.valueOf(field),
                    ReturnVisitSortDirection.valueOf(direction), null, null);
        } catch (IllegalArgumentException exception) {
            throw new ReturnVisitDomainException("return visit sort is invalid");
        }
    }

    public static ReturnVisitSort custom(
            Long fieldDefinitionId, CustomFieldType fieldType, String direction) {
        if (isBlank(direction)) {
            throw new ReturnVisitDomainException("return visit custom sort must include direction");
        }
        try {
            return new ReturnVisitSort(null, ReturnVisitSortDirection.valueOf(direction),
                    fieldDefinitionId, fieldType);
        } catch (IllegalArgumentException exception) {
            throw new ReturnVisitDomainException("return visit sort is invalid");
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
