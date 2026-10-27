package io.github.archetom.crm.domain.model;

/** Most permissive source-policy use currently allowed for an observation. */
public enum EnterpriseDataUsageMode {
    NOT_READY,
    EPHEMERAL_ONLY,
    ADOPT_SELECTED_FACTS;

    public EnterpriseDataUsageMode mostRestrictive(EnterpriseDataUsageMode other) {
        if (other == null) {
            return NOT_READY;
        }
        return ordinal() <= other.ordinal() ? this : other;
    }
}
