package io.github.archetom.crm.application.vo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Effective tenant authorization for a visible member. */
public record MemberAuthorizationVO(
        Long actorId,
        List<TenantRoleVO> roles,
        Set<String> authorities,
        Map<String, String> dataScopes
) {
}
