package io.github.archetom.crm.domain.exception;

/** A tenant opportunity conflicts with a unique field reservation. */
public final class OpportunityAlreadyExistsException extends OpportunityDomainException {

    public OpportunityAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "opportunity field value already exists");
    }
}
