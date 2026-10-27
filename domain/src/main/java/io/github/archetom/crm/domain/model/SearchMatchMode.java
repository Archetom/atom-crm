package io.github.archetom.crm.domain.model;

/** The fixed comparison semantics that an object-specific search candidate may use. */
public enum SearchMatchMode {
    /** Escaped literal prefix matching; query adapters append the only wildcard. */
    LITERAL_PREFIX,
    /** Identifier-key prefix matching after canonicalization and literal escaping. */
    NORMALIZED_PREFIX,
    /** Exact typed comparison with no LIKE expression. */
    EXACT
}
