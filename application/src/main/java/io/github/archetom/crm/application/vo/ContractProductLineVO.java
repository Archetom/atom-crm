package io.github.archetom.crm.application.vo;

import java.math.BigDecimal;

public record ContractProductLineVO(
        Long productId, String productName, String productCode,
        String categoryName, String unit, BigDecimal listPrice,
        BigDecimal unitPrice, BigDecimal quantity, BigDecimal discountRate,
        BigDecimal lineAmount, int displayOrder) {
}
