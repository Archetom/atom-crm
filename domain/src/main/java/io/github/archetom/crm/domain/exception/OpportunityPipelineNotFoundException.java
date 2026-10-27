package io.github.archetom.crm.domain.exception;

/** The requested pipeline or stage does not belong to the active tenant catalog. */
public final class OpportunityPipelineNotFoundException extends OpportunityPipelineDomainException {

    public OpportunityPipelineNotFoundException() {
        super(DomainError.NOT_FOUND, "opportunity pipeline catalog entry was not found");
    }
}
