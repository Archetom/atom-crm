package io.github.archetom.crm.domain.model;

/** Product-category lifecycle; persisted rows are never physically deleted. */
public enum ProductCategoryStatus {
    ACTIVE,
    DELETED
}
