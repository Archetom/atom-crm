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

/** One explicit department period value in an atomic batch setting command. */
@Data
@Accessors(chain = true)
public class DepartmentPerformanceTargetBatchItemRequest {
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
    @Min(0)
    @Schema(minimum = "0", description = "Required only when replacing an existing period target")
    private Long version;
}
