package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** Totals returned for the same scope and filters as a receivable list query. */
@Data
@Accessors(chain = true)
public class ReceivableListSummaryResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long count;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal totalAmount;
}
