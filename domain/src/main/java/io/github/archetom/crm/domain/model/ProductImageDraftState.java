package io.github.archetom.crm.domain.model;

/** One-shot lifecycle for Product images uploaded before the Product exists. */
public enum ProductImageDraftState {
    ACTIVE,
    CONSUMED,
    WITHDRAWN,
    EXPIRED
}
