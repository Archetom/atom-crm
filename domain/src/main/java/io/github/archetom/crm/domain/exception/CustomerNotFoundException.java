package io.github.archetom.crm.domain.exception;

/** Missing and out-of-scope customers deliberately share one failure. */
public final class CustomerNotFoundException extends CustomerDomainException {

    public CustomerNotFoundException() {
        super(DomainError.NOT_FOUND, "customer was not found");
    }
}
