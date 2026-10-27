package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;

public record WorkTaskVO(
        Long id,
        Long createdByActorId,
        Long currentAssigneeActorId,
        List<Long> followerActorIds,
        String originKind,
        Long originSeriesId,
        Long originSeriesGeneration,
        Long originOccurrenceSequence,
        LocalDateTime originNominalLocalTime,
        String originZoneId,
        Integer originOffsetSeconds,
        LocalDateTime originResolvedDueTime,
        String businessObjectType,
        Long businessRecordId,
        String title,
        String description,
        String priority,
        LocalDateTime dueTime,
        boolean overdue,
        String status,
        LocalDateTime firstStartedTime,
        Long worklistProjectionVersion,
        LocalDateTime completedTime,
        LocalDateTime cancelledTime,
        Long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
