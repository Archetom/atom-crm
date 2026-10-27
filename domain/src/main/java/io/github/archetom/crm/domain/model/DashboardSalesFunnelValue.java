package io.github.archetom.crm.domain.model;

import java.math.BigDecimal;

/** One permission-filtered opportunity aggregate for a configured sales-pipeline stage. */
public record DashboardSalesFunnelValue(
        String pipelineCode, String stageCode, long count, BigDecimal amount) {

    public DashboardSalesFunnelValue {
        if (pipelineCode == null || pipelineCode.isBlank()
                || stageCode == null || stageCode.isBlank()
                || count < 0 || amount == null) {
            throw new IllegalArgumentException("dashboard sales funnel value is invalid");
        }
    }
}
