package io.github.archetom.crm.domain.model;

/** Immutable dashboard totals sourced from append-only customer-pool event history. */
public record CustomerPoolHistorySummary(long releasedCount, long claimedCount) {

    public CustomerPoolHistorySummary {
        if (releasedCount < 0 || claimedCount < 0) {
            throw new IllegalArgumentException("customer pool history counts must not be negative");
        }
    }
}
