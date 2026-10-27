package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/** A request to join a target tenant; this does not select the caller's tenant. */
@Data
@Accessors(chain = true)
public class MembershipRequestCreateRequest {

    @Positive(message = "Target tenant ID must be positive")
    private Long targetTenantId;

    @Positive(message = "Target department ID must be positive")
    private Long targetDepartmentId;

    @Size(max = 500, message = "Request message must not exceed 500 characters")
    private String message;
}
