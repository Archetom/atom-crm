package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Tenant-scoped identity of a manually managed work task. */
@Value
public class WorkTaskId implements ValueObject<WorkTaskId> {

    Long value;

    public WorkTaskId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("work task ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(WorkTaskId other) {
        return other != null && value.equals(other.value);
    }
}
