package io.github.archetom.crm.domain.model;

import java.math.BigDecimal;

/** Visible active-receivable-plan totals for one direct customer relationship. */
public record ReceivablePlanCustomerSummary(long count, BigDecimal plannedAmount) {
}
