package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class SavedViewResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String objectType;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = SavedViewConditionResponse.class))
    private List<SavedViewConditionResponse> conditions;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Integer sortOrder;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean hidden;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean defaultView;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime createdTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
    private LocalDateTime updatedTime;
}
