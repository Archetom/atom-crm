package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/**
 * Tenant identifier used to make data ownership explicit at every boundary.
 */
@Value
public class TenantId implements ValueObject<TenantId> {

    Long value;

    public TenantId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("tenant ID must be positive");
        }
        this.value = value;
    }

    public static TenantId of(Long value) {
        return new TenantId(value);
    }

    @Override
    public boolean sameValueAs(TenantId other) {
        return other != null && value.equals(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
