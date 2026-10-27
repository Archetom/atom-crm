package io.github.archetom.crm.domain.model;

/** Governed meaning of an optional formation date; it is never inferred across sources. */
public enum FormationDateKind {
    INCORPORATED,
    REGISTERED,
    FORMED,
    ESTABLISHED,
    OPENED
}
