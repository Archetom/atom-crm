package io.github.archetom.crm.domain.exception;

/** Missing and out-of-scope leads deliberately share one failure. */
public final class LeadNotFoundException extends LeadDomainException {

    public LeadNotFoundException() {
        super(DomainError.NOT_FOUND, "lead was not found");
    }
}
