package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** Public tenant role grant. */
@Data
@Accessors(chain = true)
public class RoleGrantResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String authorityCode;
    private String dataScope;
}
