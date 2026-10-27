package io.github.archetom.crm.infra.external.gleif.runtime;

import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.Duration;
import java.time.Instant;

/** Fail-closed placeholder until deployment supplies durable cross-instance quota coordination. */
final class UnavailableGleifSharedQuota implements GleifSharedQuota {
    @Override public boolean ready() { return false; }
    @Override public boolean tryAcquire(TenantId tenantId, String sourceKey, Instant now,
                                        int maximumRequests, Duration window) { return false; }
}
