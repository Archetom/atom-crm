package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Opaque durable Work-task reminder-occurrence identity. */
@Value
public class WorkTaskReminderOccurrenceId implements ValueObject<WorkTaskReminderOccurrenceId> {
    Long value;

    public WorkTaskReminderOccurrenceId(Long value) {
        if (value == null || value <= 0) throw new IllegalArgumentException("work task reminder occurrence ID must be positive");
        this.value = value;
    }

    @Override public boolean sameValueAs(WorkTaskReminderOccurrenceId other) {
        return other != null && value.equals(other.value);
    }
}
