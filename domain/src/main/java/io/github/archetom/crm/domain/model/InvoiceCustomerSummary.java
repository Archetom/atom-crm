package io.github.archetom.crm.domain.model;

import java.math.BigDecimal;

/** Visible pending-invoice totals for one direct customer relationship. */
public record InvoiceCustomerSummary(long count, BigDecimal amount) {
}
