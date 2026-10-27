package io.github.archetom.crm.domain.model;

/** Customer lifecycle; public-pool transitions are owned by the pool module. */
public enum CustomerStatus {
    ACTIVE,
    IN_POOL,
    DELETED
}
