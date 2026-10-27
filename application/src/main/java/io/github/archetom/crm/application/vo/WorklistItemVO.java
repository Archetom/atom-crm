package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

public record WorklistItemVO(String kind, Long sourceId, Long sourceVersion, String subjectType,
                             Long subjectId, LocalDateTime dueTime, String summary) {
}
