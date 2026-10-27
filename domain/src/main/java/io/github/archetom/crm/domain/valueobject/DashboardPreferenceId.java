package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Composite identity of one member's dashboard preference in one tenant. */
@Value
public class DashboardPreferenceId implements ValueObject<DashboardPreferenceId> {

    TenantId tenantId;
    MemberId memberId;

    public DashboardPreferenceId(TenantId tenantId, MemberId memberId) {
        if (tenantId == null || memberId == null) {
            throw new IllegalArgumentException("dashboard preference identity must be complete");
        }
        this.tenantId = tenantId;
        this.memberId = memberId;
    }

    @Override
    public boolean sameValueAs(DashboardPreferenceId other) {
        return other != null && tenantId.sameValueAs(other.tenantId)
                && memberId.sameValueAs(other.memberId);
    }
}
