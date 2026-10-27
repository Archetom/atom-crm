package io.github.archetom.crm.domain.exception;

/** Stable opportunity-domain invariant violation. */
public class OpportunityDomainException extends DomainException {

    public OpportunityDomainException(String message) {
        super(message);
    }

    protected OpportunityDomainException(DomainError error, String message) {
        super(error, message);
    }
}
