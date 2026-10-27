package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Strongly typed identifier for a department performance target. */
@Value
public class DepartmentPerformanceTargetId implements ValueObject<DepartmentPerformanceTargetId> {
    Long value;

    public DepartmentPerformanceTargetId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("department performance target ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(DepartmentPerformanceTargetId other) {
        return other != null && value.equals(other.value);
    }
}
