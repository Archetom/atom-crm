package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ReportDefinitionRunResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long definitionId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long definitionVersion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String objectType;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
    private LocalDate from;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
    private LocalDate to;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"DAY", "WEEK", "MONTH"})
    private String granularity;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = ReportColumnResponse.class))
    private List<ReportColumnResponse> columns;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = ReportRowResponse.class))
    private List<ReportRowResponse> rows;

    @Data @Accessors(chain = true)
    public static class ReportColumnResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String key;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String kind;
        private String fieldCode;
        private String fieldType;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String label;
    }

    @Data @Accessors(chain = true)
    public static class ReportDimensionResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String key;
        private String label;
    }

    @Data @Accessors(chain = true)
    public static class ReportRowResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date")
        private LocalDate periodStart;
        private ReportDimensionResponse dimension;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private Map<String, BigDecimal> values;
    }
}
