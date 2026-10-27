package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Accessors(chain = true)
public class FieldLayoutResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String objectType;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> orderedCodes;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Set<String> hiddenCodes;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Set<String> pinnedCodes;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, Integer> columnWidths;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, minimum = "0")
    private Long version;
}
