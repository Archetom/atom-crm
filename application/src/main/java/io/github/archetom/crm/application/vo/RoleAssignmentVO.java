package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;

/** Application output for one explicit member-role assignment. */
public record RoleAssignmentVO(Long actorId, Long roleId, LocalDateTime createdTime) {
}
