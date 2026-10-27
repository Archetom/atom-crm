package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** Public code-defined tenant capability. */
@Data
@Accessors(chain = true)
public class AuthorityCapabilityResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean supportsDataScope;
}
