package io.github.archetom.crm.domain.exception;

/** Missing and out-of-scope opportunities deliberately share one failure. */
public final class OpportunityNotFoundException extends OpportunityDomainException {

    public OpportunityNotFoundException() {
        super(DomainError.NOT_FOUND, "opportunity was not found");
    }
}
