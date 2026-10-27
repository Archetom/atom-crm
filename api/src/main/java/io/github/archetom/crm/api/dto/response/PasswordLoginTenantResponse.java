package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** One tenant that the verified member may select for this browser session. */
@Data
@Accessors(chain = true)
public class PasswordLoginTenantResponse {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long tenantId;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    private Long departmentId;
}
