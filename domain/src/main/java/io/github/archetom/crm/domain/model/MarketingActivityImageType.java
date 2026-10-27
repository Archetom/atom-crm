package io.github.archetom.crm.domain.model;

/**
 * Closed categories for the two image groups displayed by a marketing activity.
 *
 * <p>The value belongs to the attachment relation, not to the binary itself: one controlled
 * file can only ever be related to one target, while a marketing activity keeps independent
 * limits for its cover and detail images.</p>
 */
public enum MarketingActivityImageType {
    COVER,
    DETAIL
}
