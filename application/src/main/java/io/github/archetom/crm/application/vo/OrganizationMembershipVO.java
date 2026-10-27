package io.github.archetom.crm.application.vo;

import lombok.Value;

/** Tenant membership created or inspected by organization use cases. */
@Value
public class OrganizationMembershipVO {
    Long id;
    Long tenantId;
    Long actorId;
    Long departmentId;
    String status;
    boolean active;
}
