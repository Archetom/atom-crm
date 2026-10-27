package io.github.archetom.crm.domain.model;

/** A user follow-up is mutable by its author; system entries are append-only. */
public enum ActivityKind {
    FOLLOW_UP,
    SYSTEM
}
