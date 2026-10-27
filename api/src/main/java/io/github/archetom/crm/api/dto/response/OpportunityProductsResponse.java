package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

@Data
@Accessors(chain = true)
public class OpportunityProductsResponse {
    private Long opportunityId;
    private List<OpportunityProductLineResponse> lines;
    private BigDecimal totalAmount;
    private Long version;
}
