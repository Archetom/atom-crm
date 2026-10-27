package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;

/** Read-only list aggregate after all plan and receipt scope checks have completed. */
public record ReceivablePlanListSummaryVO(
        long count, BigDecimal plannedAmount, BigDecimal receivedAmount, BigDecimal remainingAmount) {
}
