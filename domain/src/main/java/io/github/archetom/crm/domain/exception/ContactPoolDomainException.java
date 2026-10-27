package io.github.archetom.crm.domain.exception;

/** Stable contact-pool invariant violation. */
public class ContactPoolDomainException extends DomainException {

    public ContactPoolDomainException(String message) {
        super(message);
    }
}
