package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.WorkTaskId;

import java.time.LocalDateTime;

/** Lightweight B15 projection of an open Work task assigned to the current member. */
public record WorkTaskWorklistRecord(
        WorkTaskId taskId,
        long sourceVersion,
        LocalDateTime dueTime,
        String summary) {

    public WorkTaskWorklistRecord {
        if (taskId == null || sourceVersion < 0 || dueTime == null
                || summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("work task worklist record is invalid");
        }
        summary = summary.trim();
    }
}
