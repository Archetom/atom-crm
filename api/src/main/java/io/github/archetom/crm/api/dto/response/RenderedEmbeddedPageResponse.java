package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RenderedEmbeddedPageResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(nullable = true)
    private String description;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String url;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private Integer sortOrder;
}
