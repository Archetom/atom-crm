package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ContractDomainException;

/** Validated ordering for a contract page; column names never originate from request input. */
public record ContractSort(
        ContractSortField field,
        ContractSortDirection direction,
        Long customFieldDefinitionId,
        CustomFieldType customFieldType) {

    private static final ContractSort DEFAULT = new ContractSort(
            ContractSortField.UPDATED_TIME, ContractSortDirection.DESC, null, null);

    public ContractSort {
        boolean staticField = field != null;
        boolean customField = customFieldDefinitionId != null || customFieldType != null;
        if (direction == null || staticField == customField
                || customField && (customFieldDefinitionId == null || customFieldDefinitionId <= 0
                || customFieldType == null || !customFieldType.supportsListSorting())) {
            throw new ContractDomainException("contract sort is invalid");
        }
    }

    public static ContractSort from(String field, String direction) {
        if (isBlank(field) && isBlank(direction)) {
            return DEFAULT;
        }
        if (isBlank(field) || isBlank(direction)) {
            throw new ContractDomainException("contract sort must include field and direction");
        }
        try {
            return new ContractSort(ContractSortField.valueOf(field),
                    ContractSortDirection.valueOf(direction), null, null);
        } catch (IllegalArgumentException exception) {
            throw new ContractDomainException("contract sort is invalid");
        }
    }

    public static ContractSort custom(
            Long fieldDefinitionId, CustomFieldType fieldType, String direction) {
        if (isBlank(direction)) {
            throw new ContractDomainException("contract custom sort must include direction");
        }
        try {
            return new ContractSort(null, ContractSortDirection.valueOf(direction),
                    fieldDefinitionId, fieldType);
        } catch (IllegalArgumentException exception) {
            throw new ContractDomainException("contract sort is invalid");
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
