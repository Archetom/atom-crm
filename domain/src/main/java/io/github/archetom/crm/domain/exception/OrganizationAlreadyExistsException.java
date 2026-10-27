package io.github.archetom.crm.domain.exception;

/** Raised when a database-backed organization uniqueness rule is violated. */
public class OrganizationAlreadyExistsException extends OrganizationDomainException {

    public OrganizationAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "Organization resource already exists");
    }
}
