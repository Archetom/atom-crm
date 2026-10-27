package io.github.archetom.crm.domain.model;

import java.math.BigDecimal;

/** Visible open-opportunity totals for one direct customer relationship. */
public record OpportunityCustomerSummary(long count, BigDecimal estimatedAmount) {
}
