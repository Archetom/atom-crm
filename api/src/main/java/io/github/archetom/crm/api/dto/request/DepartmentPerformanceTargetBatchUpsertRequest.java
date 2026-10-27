package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Atomically creates or version-replaces explicit period values for one department and year. */
@Data
@Accessors(chain = true)
public class DepartmentPerformanceTargetBatchUpsertRequest {
    @NotNull
    @Min(1)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long departmentId;
    @NotNull
    @Pattern(regexp = "ACTIVE_CONTRACT")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"ACTIVE_CONTRACT"})
    private String metric;
    @NotNull
    @Min(2000)
    @Max(9999)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "2000", maximum = "9999")
    private Integer targetYear;
    @NotEmpty
    @Size(max = 17)
    @ArraySchema(minItems = 1, maxItems = 17,
            schema = @Schema(implementation = DepartmentPerformanceTargetBatchItemRequest.class))
    private List<@Valid DepartmentPerformanceTargetBatchItemRequest> targets;
}
