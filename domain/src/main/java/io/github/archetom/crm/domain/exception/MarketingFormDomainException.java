package io.github.archetom.crm.domain.exception;

/** Stable marketing-form invariant violation. */
public class MarketingFormDomainException extends DomainException {

    public MarketingFormDomainException(String message) {
        super(message);
    }
}
