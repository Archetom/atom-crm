package io.github.archetom.crm.domain.valueobject;

import io.github.archetom.crm.domain.model.BusinessObjectType;

public record RoleFieldPolicyId(TenantId tenantId, RoleId roleId, BusinessObjectType objectType)
        implements ValueObject<RoleFieldPolicyId> {

    public RoleFieldPolicyId {
        if (tenantId == null || roleId == null || objectType == null) {
            throw new IllegalArgumentException("role field policy ID is incomplete");
        }
    }

    @Override
    public boolean sameValueAs(RoleFieldPolicyId other) {
        return other != null && tenantId.sameValueAs(other.tenantId)
                && roleId.sameValueAs(other.roleId) && objectType == other.objectType;
    }
}
