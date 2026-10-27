package io.github.archetom.crm.domain.model;

import java.math.BigDecimal;

/** Visible active-contract totals for one direct customer relationship. */
public record ContractCustomerSummary(long count, BigDecimal totalAmount) {
}
