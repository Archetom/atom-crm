package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Tenant-scoped identity of one member's configured monthly performance target. */
@Value
public class PerformanceTargetId implements ValueObject<PerformanceTargetId> {
    Long value;

    public PerformanceTargetId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("performance target ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(PerformanceTargetId other) {
        return other != null && value.equals(other.value);
    }
}
