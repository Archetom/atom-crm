package io.github.archetom.crm.domain.exception;

/** Database-authoritative customer-pool uniqueness conflict. */
public final class CustomerPoolAlreadyExistsException extends DomainException {

    public CustomerPoolAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "customer pool conflicts with current data");
    }
}
