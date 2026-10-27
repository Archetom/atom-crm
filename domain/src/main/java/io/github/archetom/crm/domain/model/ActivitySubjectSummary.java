package io.github.archetom.crm.domain.model;

import java.time.LocalDateTime;

/** Latest active activity projection for one direct business-record subject. */
public record ActivitySubjectSummary(Long subjectId, LocalDateTime occurredTime, String content) {

    public ActivitySubjectSummary {
        if (subjectId == null || subjectId <= 0 || occurredTime == null
                || content == null || content.isBlank()) {
            throw new IllegalArgumentException("activity subject summary is invalid");
        }
    }
}
