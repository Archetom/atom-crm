package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;
import java.util.List;

public record OpportunityProductsVO(
        Long opportunityId,
        List<OpportunityProductLineVO> lines,
        BigDecimal totalAmount,
        Long version) {
}
