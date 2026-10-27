package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;

/** Server-authorized totals for the current global contract list filters. */
public record ContractListSummaryVO(
        long count, BigDecimal totalAmount, BigDecimal receivedAmount,
        BigDecimal outstandingAmount) {
}
