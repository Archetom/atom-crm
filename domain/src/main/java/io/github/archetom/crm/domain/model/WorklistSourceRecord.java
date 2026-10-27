package io.github.archetom.crm.domain.model;

import java.time.LocalDateTime;

public record WorklistSourceRecord(
        WorklistSource source,
        Long sourceId,
        Long sourceVersion,
        BusinessObjectType subjectType,
        Long subjectId,
        LocalDateTime dueTime,
        String summary) {

    public WorklistSourceRecord {
        if (source == null || sourceId == null || sourceId <= 0 || sourceVersion == null
                || sourceVersion < 0 || subjectType == null || subjectId == null || subjectId <= 0
                || dueTime == null) {
            throw new IllegalArgumentException("worklist source record is invalid");
        }
        summary = summary == null || summary.isBlank() ? null : summary.trim();
    }
}
