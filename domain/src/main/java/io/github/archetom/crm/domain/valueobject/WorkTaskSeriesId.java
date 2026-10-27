package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class WorkTaskSeriesId implements ValueObject<WorkTaskSeriesId> {
    Long value;

    public WorkTaskSeriesId(Long value) {
        if (value == null || value < 1) throw new IllegalArgumentException("work task series ID is invalid");
        this.value = value;
    }

    @Override public boolean sameValueAs(WorkTaskSeriesId other) {
        return other != null && value.equals(other.value);
    }
}
