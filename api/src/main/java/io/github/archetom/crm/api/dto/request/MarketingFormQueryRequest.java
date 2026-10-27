package io.github.archetom.crm.api.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class MarketingFormQueryRequest extends QueryRequest {
    private String status;
}
