package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Opaque identity of an authenticated Product image staging draft. */
@Value
public class ProductImageDraftId implements ValueObject<ProductImageDraftId> {
    Long value;

    public ProductImageDraftId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("product image draft ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(ProductImageDraftId other) {
        return other != null && value.equals(other.value);
    }
}
