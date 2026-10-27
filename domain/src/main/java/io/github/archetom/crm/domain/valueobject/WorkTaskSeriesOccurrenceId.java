package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

@Value
public class WorkTaskSeriesOccurrenceId implements ValueObject<WorkTaskSeriesOccurrenceId> {
    Long value;

    public WorkTaskSeriesOccurrenceId(Long value) {
        if (value == null || value < 1) throw new IllegalArgumentException("work task series occurrence ID is invalid");
        this.value = value;
    }

    @Override public boolean sameValueAs(WorkTaskSeriesOccurrenceId other) {
        return other != null && value.equals(other.value);
    }
}
