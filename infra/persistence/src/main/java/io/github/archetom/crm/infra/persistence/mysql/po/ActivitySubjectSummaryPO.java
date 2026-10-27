package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

import java.time.LocalDateTime;

/** Read-only row returned by the batched latest-activity query. */
@Data
public class ActivitySubjectSummaryPO {
    private Long subjectId;
    private LocalDateTime occurredTime;
    private String content;
}
