package io.github.archetom.crm.domain.model;

/** Stable source classification without provider account or connector details. */
public enum EnterpriseDataSourceKind {
    OFFICIAL_REGISTRY,
    OPEN_REFERENCE_INDEX,
    COMMERCIAL_AGGREGATOR,
    SYNTHETIC_FIXTURE
}
