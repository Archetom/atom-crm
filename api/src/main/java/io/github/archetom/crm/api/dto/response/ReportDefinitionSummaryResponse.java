package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class ReportDefinitionSummaryResponse {
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
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Integer measureCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Integer filterCount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime createdTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime updatedTime;
}
