package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** Fixed B17a commercial overview response. */
@Data
@Accessors(chain = true)
public class CoreReportResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long openOpportunityCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal openOpportunityEstimatedAmount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long wonOpportunityCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal wonOpportunityEstimatedAmount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long lostOpportunityCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal lostOpportunityEstimatedAmount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long activeContractCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal activeContractAmount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long receivableCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal receivableAmount;
}
