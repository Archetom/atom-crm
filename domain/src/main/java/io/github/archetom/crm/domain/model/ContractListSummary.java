package io.github.archetom.crm.domain.model;

import java.math.BigDecimal;

/**
 * Money totals for one already-authorized contract query. Values are calculated by the
 * repository after applying the same visibility and filters as the matching list request.
 */
public record ContractListSummary(
        long count, BigDecimal totalAmount, BigDecimal receivedAmount) {

    public ContractListSummary {
        if (count < 0) {
            throw new IllegalArgumentException("contract list summary count must not be negative");
        }
        totalAmount = amount(totalAmount);
        receivedAmount = amount(receivedAmount);
    }

    public BigDecimal outstandingAmount() {
        return totalAmount.subtract(receivedAmount);
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
