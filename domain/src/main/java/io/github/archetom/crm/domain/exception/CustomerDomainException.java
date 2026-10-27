package io.github.archetom.crm.domain.exception;

/** Stable customer-domain invariant violation. */
public class CustomerDomainException extends DomainException {

    public CustomerDomainException(String message) {
        super(message);
    }

    protected CustomerDomainException(DomainError error, String message) {
        super(error, message);
    }
}
