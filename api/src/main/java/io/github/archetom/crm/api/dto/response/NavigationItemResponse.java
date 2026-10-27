package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Public code-defined navigation tree node. */
@Data
@Accessors(chain = true)
public class NavigationItemResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String parentCode;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String route;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String titleKey;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private int order;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String requiredAuthority;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = NavigationItemResponse.class))
    private List<NavigationItemResponse> children;
}
