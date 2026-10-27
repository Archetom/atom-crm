package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

/** Public result of organization initialization. */
@Data
@Accessors(chain = true)
public class OrganizationMembershipResponse {
    private Long id;
    private Long tenantId;
    private Long actorId;
    private Long departmentId;
    private String status;
    private boolean active;
}
