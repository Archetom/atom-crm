package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

/** Data-only result of checking the caller's access to one target tenant. */
@Data
@Accessors(chain = true)
public class TenantAccessResponse {
    private Long tenantId;
    private Long departmentId;
    private boolean granted;
}
