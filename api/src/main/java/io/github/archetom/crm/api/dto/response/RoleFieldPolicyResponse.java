package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class RoleFieldPolicyResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long roleId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String objectType;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<RoleFieldAccessResponse> accesses;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, minimum = "0")
    private Long version;
}
