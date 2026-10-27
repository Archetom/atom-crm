package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.MemberId;

import java.math.BigDecimal;

/** One stable owner aggregate returned by a source repository. */
public record ReportRankingValue(MemberId ownerMemberId, long count, BigDecimal amount) {

    public ReportRankingValue {
        if (ownerMemberId == null || count < 0) {
            throw new IllegalArgumentException("report ranking value is invalid");
        }
        amount = amount == null ? BigDecimal.ZERO.setScale(2) : amount;
    }
}
