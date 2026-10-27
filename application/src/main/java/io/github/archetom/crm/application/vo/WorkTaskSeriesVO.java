package io.github.archetom.crm.application.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record WorkTaskSeriesVO(
        Long id, Long createdByActorId, Long currentAssigneeActorId, List<Long> followerActorIds,
        String title, String description, String priority, String recurrenceFrequency,
        Integer recurrenceInterval, List<String> weekdays, Integer monthDay, boolean lastDayOfMonth,
        String zoneId, LocalDate startLocalDate, LocalTime localTime, LocalDate endLocalDate,
        String status, Long generation, Long nextOccurrenceSequence, LocalDateTime nextNominalLocalTime,
        LocalDateTime nextDueTime, Long occurrenceCount, Long skippedCount, String lastSafeReason, Long version,
        LocalDateTime createdTime, LocalDateTime updatedTime) { }
