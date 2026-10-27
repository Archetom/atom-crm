package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class FilterConditionResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldCode;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String operator;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = String.class))
    private List<String> values;
}
