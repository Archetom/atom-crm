package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MarketingFormFieldOptionResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String value;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String label;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Integer sortOrder;
}
