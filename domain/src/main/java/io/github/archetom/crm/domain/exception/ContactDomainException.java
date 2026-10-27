package io.github.archetom.crm.domain.exception;

/** Stable contact-domain invariant violation. */
public class ContactDomainException extends DomainException {

    public ContactDomainException(String message) {
        super(message);
    }

    protected ContactDomainException(DomainError error, String message) {
        super(error, message);
    }
}
