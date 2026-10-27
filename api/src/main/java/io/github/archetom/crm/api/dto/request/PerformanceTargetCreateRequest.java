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

/** Creates one explicit member monthly target; target membership is verified server side. */
@Data
@Accessors(chain = true)
public class PerformanceTargetCreateRequest {
    @NotNull
    @Min(1)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long memberId;
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
    @Min(1)
    @Max(12)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "12")
    private Integer targetMonth;
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 17, fraction = 2)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", exclusiveMinimum = true)
    private BigDecimal amount;
}
