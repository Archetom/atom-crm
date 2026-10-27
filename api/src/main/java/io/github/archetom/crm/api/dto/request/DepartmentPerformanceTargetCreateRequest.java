package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** Creates one explicit department target at year, quarter or month granularity. */
@Data
@Accessors(chain = true)
public class DepartmentPerformanceTargetCreateRequest {
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
    @NotNull
    @Pattern(regexp = "YEAR|QUARTER|MONTH")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"YEAR", "QUARTER", "MONTH"})
    private String period;
    @NotNull
    @Min(1)
    @Max(12)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "12",
            description = "YEAR must use 1, QUARTER must use 1 through 4, MONTH must use 1 through 12")
    private Integer periodNumber;
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 17, fraction = 2)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", exclusiveMinimum = true)
    private BigDecimal amount;
}
