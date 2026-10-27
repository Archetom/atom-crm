package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

/** Non-sensitive description of the browser's current authenticated session. */
@Data
@Accessors(chain = true)
public class AuthenticationSessionResponse {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long actorId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long tenantId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant expiresAt;
}
