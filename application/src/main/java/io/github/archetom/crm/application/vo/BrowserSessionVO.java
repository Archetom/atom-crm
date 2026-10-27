package io.github.archetom.crm.application.vo;

import java.time.Instant;

/** Established browser session. The raw token is adapter-only and never enters a public response. */
public record BrowserSessionVO(Long actorId, Long tenantId, Instant expiresAt, String token) {
    @Override
    public String toString() {
        return "BrowserSessionVO[REDACTED]";
    }
}
