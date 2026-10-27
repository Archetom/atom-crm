package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;

public record ActivityVO(Long id, String subjectType, Long subjectId, String kind,
                         Long creatorActorId, String category, String purpose, String content,
                         LocalDateTime occurredTime, LocalDateTime nextContactTime,
                         List<ActivityRelationVO> relations, Long version,
                         LocalDateTime createdTime, LocalDateTime updatedTime) {
}
