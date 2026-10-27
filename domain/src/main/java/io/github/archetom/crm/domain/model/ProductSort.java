package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.ProductDomainException;

/**
 * Validated ordering for a product page. Static columns are enum-owned; custom orders retain
 * only a server-resolved definition ID and scalar type, never a caller-provided SQL name or
 * expression.
 */
public record ProductSort(
        ProductSortField field,
        ProductSortDirection direction,
        Long customFieldDefinitionId,
        CustomFieldType customFieldType) {

    private static final ProductSort DEFAULT = new ProductSort(
            ProductSortField.UPDATED_TIME, ProductSortDirection.DESC, null, null);

    public ProductSort {
        boolean staticField = field != null;
        boolean customField = customFieldDefinitionId != null || customFieldType != null;
        if (direction == null || staticField == customField
                || customField && (customFieldDefinitionId == null || customFieldDefinitionId <= 0
                || customFieldType == null || !customFieldType.supportsListSorting())) {
            throw new ProductDomainException("product sort is invalid");
        }
    }

    public static ProductSort from(String field, String direction) {
        if (isBlank(field) && isBlank(direction)) {
            return DEFAULT;
        }
        if (isBlank(field) || isBlank(direction)) {
            throw new ProductDomainException("product sort must include field and direction");
        }
        try {
            return new ProductSort(ProductSortField.valueOf(field),
                    ProductSortDirection.valueOf(direction), null, null);
        } catch (IllegalArgumentException exception) {
            throw new ProductDomainException("product sort is invalid");
        }
    }

    public static ProductSort custom(
            Long fieldDefinitionId, CustomFieldType fieldType, String direction) {
        if (isBlank(direction)) {
            throw new ProductDomainException("product custom sort must include direction");
        }
        try {
            return new ProductSort(null, ProductSortDirection.valueOf(direction),
                    fieldDefinitionId, fieldType);
        } catch (IllegalArgumentException exception) {
            throw new ProductDomainException("product sort is invalid");
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
