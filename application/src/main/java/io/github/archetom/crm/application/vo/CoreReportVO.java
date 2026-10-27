package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;

/** B17a fixed, permission-filtered commercial overview. */
public record CoreReportVO(
        long openOpportunityCount, BigDecimal openOpportunityEstimatedAmount,
        long wonOpportunityCount, BigDecimal wonOpportunityEstimatedAmount,
        long lostOpportunityCount, BigDecimal lostOpportunityEstimatedAmount,
        long activeContractCount, BigDecimal activeContractAmount,
        long receivableCount, BigDecimal receivableAmount) {
}
