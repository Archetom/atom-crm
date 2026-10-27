package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class ActivityId implements ValueObject<ActivityId> {
    Long value;

    public ActivityId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("activity ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(ActivityId other) {
        return other != null && value.equals(other.value);
    }
}
