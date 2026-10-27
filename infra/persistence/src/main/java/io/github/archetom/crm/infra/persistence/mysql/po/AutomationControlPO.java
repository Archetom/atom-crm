package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AutomationControlPO {
    private String controlKey;
    private String cadenceRevision;
    private LocalDateTime reconciledTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
