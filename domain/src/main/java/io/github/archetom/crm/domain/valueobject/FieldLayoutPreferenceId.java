package io.github.archetom.crm.domain.valueobject;

import io.github.archetom.crm.domain.model.BusinessObjectType;

public record FieldLayoutPreferenceId(TenantId tenantId, MemberId memberId,
                                      BusinessObjectType objectType)
        implements ValueObject<FieldLayoutPreferenceId> {

    public FieldLayoutPreferenceId {
        if (tenantId == null || memberId == null || objectType == null) {
            throw new IllegalArgumentException("field layout preference ID is incomplete");
        }
    }

    @Override
    public boolean sameValueAs(FieldLayoutPreferenceId other) {
        return other != null && tenantId.sameValueAs(other.tenantId)
                && memberId.sameValueAs(other.memberId) && objectType == other.objectType;
    }
}
