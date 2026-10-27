package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;

/** Application output for a tenant role and its grants. */
public record TenantRoleVO(
        Long id,
        String name,
        String description,
        String status,
        String systemKey,
        List<RoleGrantVO> grants,
        Long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
