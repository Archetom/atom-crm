package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.OpportunityDomainException;

/**
 * Validated ordering for an opportunity page. Static columns are enum-owned; custom orders retain
 * only a server-resolved definition ID and scalar type, never a caller-provided SQL name or
 * expression.
 */
public record OpportunitySort(
        OpportunitySortField field,
        OpportunitySortDirection direction,
        Long customFieldDefinitionId,
        CustomFieldType customFieldType) {

    private static final OpportunitySort DEFAULT = new OpportunitySort(
            OpportunitySortField.UPDATED_TIME, OpportunitySortDirection.DESC, null, null);

    public OpportunitySort {
        boolean staticField = field != null;
        boolean customField = customFieldDefinitionId != null || customFieldType != null;
        if (direction == null || staticField == customField
                || customField && (customFieldDefinitionId == null || customFieldDefinitionId <= 0
                || customFieldType == null || !customFieldType.supportsListSorting())) {
            throw new OpportunityDomainException("opportunity sort is invalid");
        }
    }

    public static OpportunitySort from(String field, String direction) {
        if (isBlank(field) && isBlank(direction)) {
            return DEFAULT;
        }
        if (isBlank(field) || isBlank(direction)) {
            throw new OpportunityDomainException("opportunity sort must include field and direction");
        }
        try {
            return new OpportunitySort(OpportunitySortField.valueOf(field),
                    OpportunitySortDirection.valueOf(direction), null, null);
        } catch (IllegalArgumentException exception) {
            throw new OpportunityDomainException("opportunity sort is invalid");
        }
    }

    public static OpportunitySort custom(
            Long fieldDefinitionId, CustomFieldType fieldType, String direction) {
        if (isBlank(direction)) {
            throw new OpportunityDomainException("opportunity custom sort must include direction");
        }
        try {
            return new OpportunitySort(null, OpportunitySortDirection.valueOf(direction),
                    fieldDefinitionId, fieldType);
        } catch (IllegalArgumentException exception) {
            throw new OpportunityDomainException("opportunity sort is invalid");
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
