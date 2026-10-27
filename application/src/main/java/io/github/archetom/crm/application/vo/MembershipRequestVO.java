package io.github.archetom.crm.application.vo;

import lombok.Value;

import java.time.LocalDateTime;

/** Tenant-safe representation of an auditable membership request. */
@Value
public class MembershipRequestVO {
    Long id;
    Long applicantActorId;
    Long targetTenantId;
    Long targetDepartmentId;
    String message;
    String status;
    Long reviewerActorId;
    LocalDateTime createdTime;
    LocalDateTime updatedTime;
}
