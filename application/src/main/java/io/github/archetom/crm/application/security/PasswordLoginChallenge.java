package io.github.archetom.crm.application.security;

import java.time.Instant;

/** Short-lived proof that a tenant-scoped password was verified. */
public record PasswordLoginChallenge(
        long actorId,
        long sourceTenantId,
        long userVersion,
        Instant expiresAt
) {
    public PasswordLoginChallenge {
        if (actorId <= 0 || sourceTenantId <= 0 || userVersion < 0 || expiresAt == null) {
            throw new IllegalArgumentException("Password login challenge is invalid");
        }
    }
}
