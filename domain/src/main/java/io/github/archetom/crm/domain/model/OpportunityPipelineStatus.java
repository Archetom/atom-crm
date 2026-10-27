package io.github.archetom.crm.domain.model;

/** Explicit lifecycle state shared by the Opportunity pipeline and its stage roots. */
public enum OpportunityPipelineStatus {
    ACTIVE,
    INACTIVE,
    DELETED
}
