package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;
import java.util.List;

/** Fixed B25a customer-context projection without related-record details. */
public record CustomerBusinessOverviewVO(long customerId, List<CardVO> cards) {

    public record CardVO(String source, String status, Long count, BigDecimal amount) {
    }
}
