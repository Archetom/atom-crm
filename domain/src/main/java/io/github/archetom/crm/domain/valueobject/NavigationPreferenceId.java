package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Composite identity of one member's navigation preference in one tenant. */
@Value
public class NavigationPreferenceId implements ValueObject<NavigationPreferenceId> {

    TenantId tenantId;
    MemberId memberId;

    public NavigationPreferenceId(TenantId tenantId, MemberId memberId) {
        if (tenantId == null || memberId == null) {
            throw new IllegalArgumentException("navigation preference identity must be complete");
        }
        this.tenantId = tenantId;
        this.memberId = memberId;
    }

    @Override
    public boolean sameValueAs(NavigationPreferenceId other) {
        return other != null && tenantId.sameValueAs(other.tenantId) && memberId.sameValueAs(other.memberId);
    }
}
