package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class MarketingFormFieldResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldCode;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String label;
    private String placeholder;
    private String helpText;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean required;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Integer sortOrder;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
            "TEXT", "TEXTAREA", "SELECT", "MULTI_SELECT", "DATE", "DATETIME",
            "INTEGER", "DECIMAL", "BOOLEAN", "EMAIL", "PHONE", "URL", "FILE"})
    private String fieldType;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = MarketingFormFieldOptionResponse.class))
    private List<MarketingFormFieldOptionResponse> options;
}
