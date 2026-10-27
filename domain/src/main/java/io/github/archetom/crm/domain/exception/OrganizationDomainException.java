package io.github.archetom.crm.domain.exception;

/** Stable organization-domain invariant violation. */
public class OrganizationDomainException extends DomainException {

    public OrganizationDomainException(String message) {
        super(message);
    }

    protected OrganizationDomainException(DomainError error, String message) {
        super(error, message);
    }
}
