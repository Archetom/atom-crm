package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AutomationSchedulePO {
    private Long tenantId;
    private String kind;
    private String status;
    private Long generation;
    private String cadenceRevision;
    private LocalDateTime nextDueAt;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
