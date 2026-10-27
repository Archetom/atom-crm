package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Tenant-scoped customer aggregate identifier. */
@Value
public class CustomerId implements ValueObject<CustomerId> {

    Long value;

    public CustomerId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("customer ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(CustomerId other) {
        return other != null && value.equals(other.value);
    }
}
