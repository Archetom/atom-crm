package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Tenant-scoped product aggregate identifier. */
@Value
public class ProductId implements ValueObject<ProductId> {

    Long value;

    public ProductId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("product ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(ProductId other) {
        return other != null && value.equals(other.value);
    }
}
