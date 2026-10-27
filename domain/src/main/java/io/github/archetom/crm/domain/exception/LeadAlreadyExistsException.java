package io.github.archetom.crm.domain.exception;

/** A tenant lead conflicts with a unique field reservation. */
public final class LeadAlreadyExistsException extends LeadDomainException {

    public LeadAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "lead field value already exists");
    }
}
