package io.github.archetom.crm.domain.model;

/** Counts visible customers with a follow-up in one window and newly created customers without one. */
public record CustomerFollowUpSummary(long followedCount, long newUnfollowedCount) {

    public CustomerFollowUpSummary {
        if (followedCount < 0 || newUnfollowedCount < 0) {
            throw new IllegalArgumentException("customer follow-up counts must not be negative");
        }
    }
}
