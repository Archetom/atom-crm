package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record MarketingActivityVO(
        Long id, String name, String relatedObjectType, Long createdByActorId,
        Long marketingFormId, String marketingFormPublicId,
        List<Long> participantActorIds, List<Long> participantDepartmentIds,
        String activityType, LocalDateTime startTime,
        LocalDateTime endTime, BigDecimal budget, String address, String description,
        long viewCount, long submissionCount, boolean oneSubmissionPerUser, String status, Long version,
        LocalDateTime createdTime, LocalDateTime updatedTime) {
    public MarketingActivityVO {
        participantActorIds = participantActorIds == null ? List.of() : List.copyOf(participantActorIds);
        participantDepartmentIds = participantDepartmentIds == null
                ? List.of()
                : List.copyOf(participantDepartmentIds);
    }
}
