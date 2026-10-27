package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Reviewed management projection for one member's monthly performance target. */
@Data
@Accessors(chain = true)
public class PerformanceTargetResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long memberId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"ACTIVE_CONTRACT"})
    private String metric;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "2000", maximum = "9999")
    private Integer targetYear;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "12")
    private Integer targetMonth;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", exclusiveMinimum = true)
    private BigDecimal amount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime createdTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime updatedTime;
}
