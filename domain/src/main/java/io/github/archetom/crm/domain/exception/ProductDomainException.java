package io.github.archetom.crm.domain.exception;

/** Stable product-catalog invariant failure. */
public class ProductDomainException extends DomainException {

    public ProductDomainException(String message) {
        super(message);
    }

    protected ProductDomainException(DomainError error, String message) {
        super(error, message);
    }
}
