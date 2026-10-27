package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorklistSourceRecordPO {
    private String source;
    private Long sourceId;
    private Long sourceVersion;
    private String subjectType;
    private Long subjectId;
    private LocalDateTime dueTime;
    private String summary;
}
