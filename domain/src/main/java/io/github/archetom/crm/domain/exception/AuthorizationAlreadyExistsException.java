package io.github.archetom.crm.domain.exception;

/** Stable conflict for duplicate role names or assignments. */
public final class AuthorizationAlreadyExistsException extends AuthorizationDomainException {

    public AuthorizationAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "Authorization resource already exists");
    }
}
