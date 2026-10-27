package io.github.archetom.crm.domain.model;

/** Lifecycle for a tenant marketing form. Only active forms are publicly available. */
public enum MarketingFormStatus {
    INACTIVE,
    ACTIVE,
    DELETED
}
