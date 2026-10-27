package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

import java.time.LocalDateTime;

/** Projection-only low-cardinality ledger metrics; never maps a business identity. */
@Data
public class AutomationMetricPO {
    private String kind;
    private String status;
    private Long count;
    private LocalDateTime latestSuccessUtc;
}
