package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.model.WorkTaskSeriesOccurrenceStatus;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId;
import io.github.archetom.crm.domain.valueobject.WorkTaskSeriesOccurrenceId;

import java.time.LocalDateTime;

/** Paged immutable recurrence fact; it is never hydrated as an unbounded series child. */
public record WorkTaskSeriesOccurrence(
        WorkTaskSeriesOccurrenceId id,
        TenantId tenantId,
        WorkTaskSeriesId seriesId,
        long seriesGeneration,
        long occurrenceSequence,
        WorkTaskSeriesOccurrenceStatus status,
        LocalDateTime nominalLocalTime,
        String zoneId,
        int resolvedOffsetSeconds,
        LocalDateTime resolvedDueTime,
        WorkTaskId generatedTaskId,
        String safeReason,
        LocalDateTime createdTime
) {
    public WorkTaskSeriesOccurrence {
        if (tenantId == null || seriesId == null || seriesGeneration < 1 || occurrenceSequence < 1
                || status == null || nominalLocalTime == null || zoneId == null || zoneId.isBlank()
                || resolvedDueTime == null || createdTime == null
                || status == WorkTaskSeriesOccurrenceStatus.MATERIALIZED && generatedTaskId == null
                || status == WorkTaskSeriesOccurrenceStatus.SKIPPED && (generatedTaskId != null
                || safeReason == null || safeReason.isBlank())) {
            throw new IllegalArgumentException("work task series occurrence is invalid");
        }
    }
}
