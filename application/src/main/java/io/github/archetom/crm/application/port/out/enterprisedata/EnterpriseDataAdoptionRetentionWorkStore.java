package io.github.archetom.crm.application.port.out.enterprisedata;

import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Durable, tenant-carrying work leases for value-free B23 adoption-evidence deletion. */
public interface EnterpriseDataAdoptionRetentionWorkStore {
    void schedule(TenantId tenantId, long adoptionId, Instant deleteAfter);

    Optional<Lease> claimDue(Instant now, Duration leaseDuration, UUID owner);

    void release(Lease lease);

    record Lease(long workId, TenantId tenantId, long adoptionId, long fence, UUID owner) {
        public Lease {
            if (workId < 1 || tenantId == null || adoptionId < 1 || fence < 1 || owner == null) {
                throw new IllegalArgumentException("enterprise data adoption retention lease is invalid");
            }
        }
    }
}
