package io.github.archetom.crm.domain.exception;

/** Database-authoritative contact-pool uniqueness conflict. */
public final class ContactPoolAlreadyExistsException extends DomainException {

    public ContactPoolAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "contact pool conflicts with current data");
    }
}
