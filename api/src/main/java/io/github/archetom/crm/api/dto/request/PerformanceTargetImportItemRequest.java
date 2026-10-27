package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** One member-month row decoded from a local performance-target CSV import. */
@Data
@Accessors(chain = true)
public class PerformanceTargetImportItemRequest {
    @NotNull
    @Min(1)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long memberId;
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
