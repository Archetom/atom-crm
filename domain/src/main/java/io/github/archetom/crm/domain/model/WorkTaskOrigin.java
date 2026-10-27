package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.WorkTaskDomainException;

import java.time.LocalDateTime;

/** Immutable creation provenance retained by an ordinary WorkTask. */
public record WorkTaskOrigin(
        Kind kind,
        Long seriesId,
        Long seriesGeneration,
        Long occurrenceSequence,
        LocalDateTime nominalLocalTime,
        String zoneId,
        Integer resolvedOffsetSeconds,
        LocalDateTime resolvedDueTime
) {
    public enum Kind { MANUAL, RECURRENCE }

    public WorkTaskOrigin {
        if (kind == null) {
            throw new WorkTaskDomainException("work task origin kind is required");
        }
        if (kind == Kind.MANUAL) {
            if (seriesId != null || seriesGeneration != null || occurrenceSequence != null
                    || nominalLocalTime != null || zoneId != null || resolvedOffsetSeconds != null
                    || resolvedDueTime != null) {
                throw new WorkTaskDomainException("manual work task origin is invalid");
            }
        } else if (seriesId == null || seriesId < 1 || seriesGeneration == null
                || seriesGeneration < 1 || occurrenceSequence == null || occurrenceSequence < 1
                || nominalLocalTime == null || zoneId == null || zoneId.isBlank()
                || resolvedOffsetSeconds == null || resolvedDueTime == null) {
            throw new WorkTaskDomainException("recurring work task origin is invalid");
        }
    }

    public static WorkTaskOrigin manual() {
        return new WorkTaskOrigin(Kind.MANUAL, null, null, null, null, null, null, null);
    }

    public static WorkTaskOrigin recurrence(long seriesId, long generation, long sequence,
                                            LocalDateTime nominalLocalTime, String zoneId,
                                            int resolvedOffsetSeconds, LocalDateTime resolvedDueTime) {
        return new WorkTaskOrigin(Kind.RECURRENCE, seriesId, generation, sequence,
                nominalLocalTime, zoneId, resolvedOffsetSeconds, resolvedDueTime);
    }

    public boolean isRecurring() {
        return kind == Kind.RECURRENCE;
    }
}
