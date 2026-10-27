package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Atomic department-target import after the browser has locally parsed a bounded CSV source. */
@Data
@Accessors(chain = true)
public class DepartmentPerformanceTargetImportRequest {
    @NotNull
    @Pattern(regexp = "ACTIVE_CONTRACT")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"ACTIVE_CONTRACT"})
    private String metric;
    @NotEmpty
    @Size(max = 200)
    @ArraySchema(schema = @Schema(implementation = DepartmentPerformanceTargetImportItemRequest.class))
    private List<@Valid DepartmentPerformanceTargetImportItemRequest> targets;
}
