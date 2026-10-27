package io.github.archetom.crm.application.automation;

import java.time.Duration;

/** One durable occurrence was materialized and is safe to observe without identities. */
public record AutomationMaterialization(AutomationKind kind, Duration dueLag) {
    public AutomationMaterialization {
        if (kind == null || dueLag == null || dueLag.isNegative()) {
            throw new IllegalArgumentException("automation materialization is invalid");
        }
    }
}
