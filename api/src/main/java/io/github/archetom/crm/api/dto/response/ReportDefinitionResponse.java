package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class ReportDefinitionResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    private String description;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String objectType;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String timeFieldCode;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"DAY", "WEEK", "MONTH"})
    private String granularity;
    private String dimensionFieldCode;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = FilterConditionResponse.class))
    private List<FilterConditionResponse> filters;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = ReportMeasureResponse.class))
    private List<ReportMeasureResponse> measures;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime createdTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime updatedTime;

    @Data
    @Accessors(chain = true)
    public static class ReportMeasureResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"COUNT", "SUM"})
        private String type;
        private String fieldCode;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String key;
    }
}
