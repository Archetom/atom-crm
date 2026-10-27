package io.github.archetom.crm.domain.exception;

/** A permanent exact pipeline or stage code identity is already reserved. */
public final class OpportunityPipelineAlreadyExistsException extends OpportunityPipelineDomainException {

    public OpportunityPipelineAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "opportunity pipeline catalog code already exists");
    }
}
