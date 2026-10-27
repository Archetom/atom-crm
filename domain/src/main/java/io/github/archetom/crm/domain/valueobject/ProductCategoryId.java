package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Tenant-scoped product-category identifier. */
@Value
public class ProductCategoryId implements ValueObject<ProductCategoryId> {

    Long value;

    public ProductCategoryId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("product category ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(ProductCategoryId other) {
        return other != null && value.equals(other.value);
    }
}
