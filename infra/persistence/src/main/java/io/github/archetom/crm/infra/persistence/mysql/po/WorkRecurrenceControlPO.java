package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkRecurrenceControlPO {
    private String controlKey;
    private String tzdbFingerprint;
    private String policyRevision;
    private String reconciliationStatus;
    private String targetTzdbFingerprint;
    private String targetPolicyRevision;
    private Long cursorTenantId;
    private Long cursorSeriesId;
    private LocalDateTime reconciledTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
