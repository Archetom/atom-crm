package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** Public department tree node. */
@Data
@Accessors(chain = true)
public class DepartmentResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    private Long parentDepartmentId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean active;
}
