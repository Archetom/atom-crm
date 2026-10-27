package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** Public representation of a request to join an initialized tenant. */
@Data
@Accessors(chain = true)
public class MembershipRequestResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long applicantActorId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetTenantId;
    private Long targetDepartmentId;
    private String message;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    private Long reviewerActorId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updatedTime;
}
