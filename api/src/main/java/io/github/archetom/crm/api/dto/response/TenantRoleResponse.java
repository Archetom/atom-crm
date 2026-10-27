package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/** Public tenant role representation. */
@Data
@Accessors(chain = true)
public class TenantRoleResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    private String description;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    private String systemKey;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<RoleGrantResponse> grants;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updatedTime;
}
