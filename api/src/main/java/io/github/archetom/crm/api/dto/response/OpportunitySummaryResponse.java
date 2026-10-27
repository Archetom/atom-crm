package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class OpportunitySummaryResponse {
    private Long count;
    private BigDecimal estimatedAmount;
}
