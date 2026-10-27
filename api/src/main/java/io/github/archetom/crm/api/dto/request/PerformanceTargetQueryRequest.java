package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.experimental.Accessors;

/** Query parameters for one target member and calendar year. */
@Data
@Accessors(chain = true)
public class PerformanceTargetQueryRequest {
    @NotNull
    @Min(1)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long memberId;
    @NotNull
    @Min(2000)
    @Max(9999)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "2000", maximum = "9999")
    private Integer targetYear;
    @Pattern(regexp = "ACTIVE_CONTRACT")
    @Schema(allowableValues = {"ACTIVE_CONTRACT"})
    private String metric;
}
