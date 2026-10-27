package io.github.archetom.crm.domain.model;

/** Counts visible active contracts ending in a dashboard window on either side of its as-of date. */
public record ContractExpirationSummary(long expiringCount, long expiredCount) {

    public ContractExpirationSummary {
        if (expiringCount < 0 || expiredCount < 0) {
            throw new IllegalArgumentException("contract expiration counts must not be negative");
        }
    }
}
