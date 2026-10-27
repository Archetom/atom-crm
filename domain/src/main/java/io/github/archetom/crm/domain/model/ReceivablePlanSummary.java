package io.github.archetom.crm.domain.model;

import java.math.BigDecimal;

/**
 * Server-calculated aggregate over the caller-visible receivable-plan list predicate.
 *
 * <p>The received amount is separately bounded by the caller's receivable visibility;
 * it must never be inferred from a plan row or client cache.</p>
 */
public record ReceivablePlanSummary(
        long count, BigDecimal plannedAmount, BigDecimal receivedAmount) {
    public ReceivablePlanSummary {
        if (count < 0 || plannedAmount == null || receivedAmount == null) {
            throw new IllegalArgumentException("receivable plan summary is invalid");
        }
    }

    public BigDecimal remainingAmount() {
        return plannedAmount.subtract(receivedAmount);
    }
}
