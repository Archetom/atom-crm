package io.github.archetom.crm.application.port.out.enterprisedata;

import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.Duration;
import java.time.Instant;

/** Durable, cross-instance source quota arbitration with no query history or source payload. */
public interface EnterpriseDataSharedQuota {
    boolean ready();

    boolean tryAcquire(TenantId tenantId, String sourceKey, Instant now,
                       int maximumRequests, Duration window);
}
