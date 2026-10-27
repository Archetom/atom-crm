package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Accessors(chain = true)
public class ReportTrendResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"WON_OPPORTUNITY", "ACTIVE_CONTRACT", "ACTIVE_RECEIVABLE"})
    private String metric;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
    private LocalDate from;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
    private LocalDate to;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"DAY", "WEEK", "MONTH"})
    private String granularity;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = ReportTrendPointResponse.class))
    private List<ReportTrendPointResponse> points;

    @Data
    @Accessors(chain = true)
    public static class ReportTrendPointResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
        private LocalDate periodStart;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        private Long count;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal amount;
    }
}
