package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("crm_opportunity_product_line")
public class OpportunityProductLinePO {
    private Long tenantId;
    private Long opportunityId;
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
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
