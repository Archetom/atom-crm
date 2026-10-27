package io.github.archetom.crm.domain.exception;

/** A tenant contact conflicts with a unique field reservation. */
public final class ContactAlreadyExistsException extends ContactDomainException {

    public ContactAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "contact field value already exists");
    }
}
