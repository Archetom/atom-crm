package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class OpportunityProductLineResponse {
    private Long productId;
    private String productName;
    private String productCode;
    private String categoryName;
    private String unit;
    private BigDecimal listPrice;
    private BigDecimal unitPrice;
    private BigDecimal quantity;
    private BigDecimal discountRate;
    private BigDecimal lineAmount;
    private Integer displayOrder;
}
