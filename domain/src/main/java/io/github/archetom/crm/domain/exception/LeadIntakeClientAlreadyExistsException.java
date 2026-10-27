package io.github.archetom.crm.domain.exception;

public final class LeadIntakeClientAlreadyExistsException extends LeadDomainException {
    public LeadIntakeClientAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "lead intake client key ID already exists");
    }
}
