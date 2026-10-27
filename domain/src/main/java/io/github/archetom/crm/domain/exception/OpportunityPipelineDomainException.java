package io.github.archetom.crm.domain.exception;

/** Stable invariant violation in the tenant Opportunity sales-pipeline catalog. */
public class OpportunityPipelineDomainException extends DomainException {

    public OpportunityPipelineDomainException(String message) {
        super(message);
    }

    protected OpportunityPipelineDomainException(DomainError error, String message) {
        super(error, message);
    }
}
