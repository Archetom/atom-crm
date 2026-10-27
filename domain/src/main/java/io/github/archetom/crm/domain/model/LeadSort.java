package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.LeadDomainException;

/**
 * Validated ordering for a lead page. Static columns are enum-owned; custom orders retain only
 * a server-resolved definition ID and scalar type, never a caller-provided SQL name or expression.
 */
public record LeadSort(
        LeadSortField field,
        LeadSortDirection direction,
        Long customFieldDefinitionId,
        CustomFieldType customFieldType) {

    private static final LeadSort DEFAULT = new LeadSort(
            LeadSortField.UPDATED_TIME, LeadSortDirection.DESC, null, null);

    public LeadSort {
        boolean staticField = field != null;
        boolean customField = customFieldDefinitionId != null || customFieldType != null;
        if (direction == null || staticField == customField
                || customField && (customFieldDefinitionId == null || customFieldDefinitionId <= 0
                || customFieldType == null || !customFieldType.supportsListSorting())) {
            throw new LeadDomainException("lead sort is invalid");
        }
    }

    public static LeadSort from(String field, String direction) {
        if (isBlank(field) && isBlank(direction)) {
            return DEFAULT;
        }
        if (isBlank(field) || isBlank(direction)) {
            throw new LeadDomainException("lead sort must include field and direction");
        }
        try {
            return new LeadSort(LeadSortField.valueOf(field), LeadSortDirection.valueOf(direction),
                    null, null);
        } catch (IllegalArgumentException exception) {
            throw new LeadDomainException("lead sort is invalid");
        }
    }

    public static LeadSort custom(
            Long fieldDefinitionId, CustomFieldType fieldType, String direction) {
        if (isBlank(direction)) {
            throw new LeadDomainException("lead custom sort must include direction");
        }
        try {
            return new LeadSort(null, LeadSortDirection.valueOf(direction),
                    fieldDefinitionId, fieldType);
        } catch (IllegalArgumentException exception) {
            throw new LeadDomainException("lead sort is invalid");
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
