package io.github.archetom.crm.domain.event;

import io.github.archetom.crm.domain.valueobject.TenantId;

/** Tenant-wide signal emitted after authorization state has committed. */
public final class TenantAuthorizationChangedEvent extends BaseDomainEvent {

    private final TenantId tenantId;

    public TenantAuthorizationChangedEvent(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        this.tenantId = tenantId;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    @Override
    public String getAggregateId() {
        return tenantId.getValue().toString();
    }
}
