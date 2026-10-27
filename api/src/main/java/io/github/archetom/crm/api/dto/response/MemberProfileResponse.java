package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** Public local member profile. Identity and tenant selection fields are absent. */
@Data
@Accessors(chain = true)
public class MemberProfileResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long actorId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;
    private String email;
    private String maskedPhoneNumber;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean active;
}
