package io.github.archetom.crm.domain.exception;

/** Stable lead-domain invariant violation. */
public class LeadDomainException extends DomainException {

    public LeadDomainException(String message) {
        super(message);
    }

    protected LeadDomainException(DomainError error, String message) {
        super(error, message);
    }
}
