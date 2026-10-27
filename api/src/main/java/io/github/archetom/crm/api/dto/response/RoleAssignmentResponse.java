package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** Public explicit member-role assignment. */
@Data
@Accessors(chain = true)
public class RoleAssignmentResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long actorId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long roleId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdTime;
}
