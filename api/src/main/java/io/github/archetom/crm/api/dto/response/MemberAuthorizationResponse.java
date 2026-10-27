package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Effective tenant roles, authorities, and scoped policies for one visible member. */
@Data
@Accessors(chain = true)
public class MemberAuthorizationResponse {
    private Long actorId;
    private List<TenantRoleResponse> roles;
    private Set<String> authorities;
    private Map<String, String> dataScopes;
}
