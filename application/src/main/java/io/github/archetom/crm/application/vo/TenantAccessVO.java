package io.github.archetom.crm.application.vo;

import lombok.Value;

/** Result of inspecting an actor's local membership for one target tenant. */
@Value
public class TenantAccessVO {
    Long tenantId;
    Long departmentId;
    boolean granted;
}
