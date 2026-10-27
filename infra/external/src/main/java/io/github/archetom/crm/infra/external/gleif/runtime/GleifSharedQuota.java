package io.github.archetom.crm.infra.external.gleif.runtime;

import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.Duration;
import java.time.Instant;

/**
 * Private deployment seam for a cross-instance GLEIF request quota. Implementations receive a
 * verified tenant solely for accountability; callers never receive another tenant's usage.
 */
public interface GleifSharedQuota {
    boolean ready();

    boolean tryAcquire(TenantId tenantId, String sourceKey, Instant now,
                       int maximumRequests, Duration window);
}
