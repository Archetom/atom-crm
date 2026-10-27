package io.github.archetom.crm.domain.exception;

/** A tenant customer conflicts with a unique field reservation. */
public final class CustomerAlreadyExistsException extends CustomerDomainException {

    public CustomerAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "customer field value already exists");
    }
}
