package io.github.archetom.crm.application.port.out.enterprisedata;

import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.Instant;
import java.util.Objects;

/** Ephemeral, caller-bound preview; implementations must never persist it to business storage. */
public record EnterpriseDataPreview(
        TenantId tenantId,
        MemberId memberId,
        CustomerId customerId,
        long customerVersion,
        String addressSlotKey,
        EnterpriseDataObservation observation,
        EnterpriseDataAdoptionPolicy policy,
        Instant observedAt,
        Instant expiresAt
) {
    public EnterpriseDataPreview {
        Objects.requireNonNull(tenantId, "preview tenant must not be null");
        Objects.requireNonNull(memberId, "preview member must not be null");
        Objects.requireNonNull(customerId, "preview customer must not be null");
        if (customerVersion < 0 || addressSlotKey == null || addressSlotKey.isBlank()) {
            throw new IllegalArgumentException("enterprise data preview target is invalid");
        }
        Objects.requireNonNull(observation, "preview observation must not be null");
        Objects.requireNonNull(policy, "preview policy must not be null");
        Objects.requireNonNull(observedAt, "preview observed time must not be null");
        Objects.requireNonNull(expiresAt, "preview expiry must not be null");
        if (!policy.ready() || !expiresAt.isAfter(observedAt)) {
            throw new IllegalArgumentException("enterprise data preview is invalid");
        }
    }

    @Override
    public String toString() {
        return "EnterpriseDataPreview[redacted]";
    }
}
