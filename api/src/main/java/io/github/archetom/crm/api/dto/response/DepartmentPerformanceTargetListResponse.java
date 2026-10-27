package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Named response wrapper keeps department target settings explicit in the API contract. */
@Data
@Accessors(chain = true)
public class DepartmentPerformanceTargetListResponse {
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = DepartmentPerformanceTargetResponse.class))
    private List<DepartmentPerformanceTargetResponse> targets;
}
