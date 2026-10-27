package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** Aggregate of the server-authorized receivable-plan list predicate. */
@Data
@Accessors(chain = true)
@Schema(description = "Server-calculated receivable-plan list aggregate")
public class ReceivablePlanListSummaryResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long count;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal plannedAmount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal receivedAmount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal remainingAmount;
}
