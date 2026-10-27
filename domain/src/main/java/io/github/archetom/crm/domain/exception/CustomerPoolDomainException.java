package io.github.archetom.crm.domain.exception;

/** Stable customer-pool invariant violation. */
public class CustomerPoolDomainException extends DomainException {

    public CustomerPoolDomainException(String message) {
        super(message);
    }
}
