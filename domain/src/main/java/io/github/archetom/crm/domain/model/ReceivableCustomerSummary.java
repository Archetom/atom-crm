package io.github.archetom.crm.domain.model;

import java.math.BigDecimal;

/** Visible active-receivable totals for one direct customer relationship. */
public record ReceivableCustomerSummary(long count, BigDecimal amount) {
}
