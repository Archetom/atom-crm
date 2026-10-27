package io.github.archetom.crm.domain.model;

/** Bounded tenant catalog query for stable Opportunity pipeline or stage ordering. */
public record OpportunityPipelineQuery(
        String keyword, OpportunityPipelineStatus status, int page, int size) {

    public OpportunityPipelineQuery {
        if (page < 1 || size < 1 || size > 200) {
            throw new IllegalArgumentException("opportunity pipeline query page is invalid");
        }
    }
}
