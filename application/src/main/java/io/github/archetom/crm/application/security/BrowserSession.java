package io.github.archetom.crm.application.security;

import java.time.Instant;

/** Verified browser-session identity, bound to a current tenant and account version. */
public record BrowserSession(
        long actorId,
        long sourceTenantId,
        long tenantId,
        long userVersion,
        Instant expiresAt
) {
    public BrowserSession {
        if (actorId <= 0 || sourceTenantId <= 0 || tenantId <= 0 || userVersion < 0 || expiresAt == null) {
            throw new IllegalArgumentException("Browser session is invalid");
        }
    }
}
