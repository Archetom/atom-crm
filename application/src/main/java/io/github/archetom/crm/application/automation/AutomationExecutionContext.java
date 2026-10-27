package io.github.archetom.crm.application.automation;

import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Exact, claimed scheduled-automation identity. It deliberately has no human,
 * connector, authority, request, or arbitrary payload information.
 */
public record AutomationExecutionContext(
        long runId,
        TenantId tenantId,
        AutomationKind kind,
        long generation,
        UUID leaseOwner,
        long leaseFence,
        int attempt,
        LocalDateTime dueAt,
        LocalDateTime evaluationTime) {

    public AutomationExecutionContext {
        if (runId < 1 || tenantId == null || kind == null || generation < 1
                || leaseOwner == null || leaseFence < 1 || attempt < 1
                || dueAt == null || evaluationTime == null) {
            throw new IllegalArgumentException("automation execution context is invalid");
        }
        Objects.requireNonNull(tenantId, "tenantId");
    }
}
