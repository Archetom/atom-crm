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
public class ReportRankingResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"WON_OPPORTUNITY", "ACTIVE_CONTRACT", "ACTIVE_RECEIVABLE"})
    private String metric;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
    private LocalDate from;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
    private LocalDate to;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = ReportRankingItemResponse.class))
    private List<ReportRankingItemResponse> items;

    @Data
    @Accessors(chain = true)
    public static class ReportRankingItemResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        private Integer rank;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        private Long ownerActorId;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        private Long count;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal amount;
    }
}
