package io.github.archetom.crm.domain.model;

/** Lifecycle standing of an identifier, independent from the entity status. */
public enum IdentifierStanding {
    CURRENT,
    STALE,
    TRANSITIONING,
    SUPERSEDED,
    INVALID,
    RETIRED,
    UNKNOWN
}
