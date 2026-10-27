package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;

/** Server-authorized totals for the current global receivable list filters. */
public record ReceivableListSummaryVO(long count, BigDecimal totalAmount) {
}
