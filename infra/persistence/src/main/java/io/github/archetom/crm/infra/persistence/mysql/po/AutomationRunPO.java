package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AutomationRunPO {
    private Long id;
    private Long tenantId;
    private String kind;
    private Long generation;
    private LocalDateTime dueAt;
    private LocalDateTime evaluationTime;
    private String status;
    private LocalDateTime availableAt;
    private Integer attempt;
    private String leaseOwner;
    private LocalDateTime leaseExpiresAt;
    private Long leaseFence;
    private LocalDateTime deadlineAt;
    private String resultCode;
    private String safeReason;
    private Integer affectedCount;
    private LocalDateTime completedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
