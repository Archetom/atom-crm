package io.github.archetom.crm.domain.exception;

/** Stable customization-domain invariant violation. */
public class CustomizationDomainException extends DomainException {

    public CustomizationDomainException(String message) {
        super(message);
    }

    protected CustomizationDomainException(DomainError error, String message) {
        super(error, message);
    }
}
