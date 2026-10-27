package io.github.archetom.crm.domain.model;

/** Contact lifecycle; public-pool transitions are owned by the pool module. */
public enum ContactStatus {
    ACTIVE,
    IN_POOL,
    DELETED
}
