package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RoleFieldAccessResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long fieldId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldCode;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;
    @Schema(allowableValues = {"HIDDEN", "READ", "WRITE"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String accessLevel;
    @Schema(allowableValues = {"NONE", "PARTIAL", "FULL"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String maskType;
}
