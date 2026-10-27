package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Named response wrapper keeps the dashboard target catalog schema explicit. */
@Data
@Accessors(chain = true)
public class PerformanceTargetListResponse {
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = PerformanceTargetResponse.class))
    private List<PerformanceTargetResponse> targets;
}
