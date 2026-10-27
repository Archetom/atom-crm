package io.github.archetom.crm.domain.exception;

/** A resolved recurrence timestamp cannot be represented by persistent storage. */
public final class WorkRecurrenceTimeRangeExceededException extends WorkTaskDomainException {
    public WorkRecurrenceTimeRangeExceededException(String message) {
        super(message);
    }
}
