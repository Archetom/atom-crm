package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.util.List;

@Data
@Accessors(chain = true)
public class MarketingFormSubmissionTrendResponse {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long formId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
    private LocalDate from;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
    private LocalDate to;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"DAY", "WEEK", "MONTH"})
    private String granularity;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = MarketingFormSubmissionTrendPointResponse.class))
    private List<MarketingFormSubmissionTrendPointResponse> points;

    @Data
    @Accessors(chain = true)
    public static class MarketingFormSubmissionTrendPointResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
        private LocalDate periodStart;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        private Long acceptedCount;
    }
}
