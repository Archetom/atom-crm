package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * Contract product fields safe for the dedicated relation-management baseline.
 * Derived discounts and contract amounts are intentionally not exposed here.
 */
@Data
@Accessors(chain = true)
public class ContractProductSnapshotResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long productId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String productName;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String productCode;
    private String categoryName;
    private String unit;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private BigDecimal quantity;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private BigDecimal unitPrice;
}
