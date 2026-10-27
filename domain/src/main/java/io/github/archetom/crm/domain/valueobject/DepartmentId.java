package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Positive persisted identity of a tenant-owned department. */
@Value
public class DepartmentId implements ValueObject<DepartmentId> {

    Long value;

    public DepartmentId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("department ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(DepartmentId other) {
        return other != null && value.equals(other.value);
    }
}
