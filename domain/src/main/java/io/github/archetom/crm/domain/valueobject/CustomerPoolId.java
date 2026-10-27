package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Tenant-scoped customer-pool identifier. */
@Value
public class CustomerPoolId implements ValueObject<CustomerPoolId> {

    Long value;

    public CustomerPoolId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("customer pool ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(CustomerPoolId other) {
        return other != null && value.equals(other.value);
    }
}
