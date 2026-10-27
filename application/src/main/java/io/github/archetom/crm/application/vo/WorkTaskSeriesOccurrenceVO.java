package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record WorkTaskSeriesOccurrenceVO(
        Long id, Long seriesId, Long seriesGeneration, Long occurrenceSequence, String status,
        LocalDateTime nominalLocalTime, String zoneId, Integer resolvedOffsetSeconds,
        LocalDateTime resolvedDueTime, Long generatedWorkTaskId, String safeReason, LocalDateTime createdTime) { }
