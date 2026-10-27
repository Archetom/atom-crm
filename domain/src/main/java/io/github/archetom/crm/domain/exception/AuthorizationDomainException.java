package io.github.archetom.crm.domain.exception;

/** Stable authorization-domain invariant violation. */
public class AuthorizationDomainException extends DomainException {

    public AuthorizationDomainException(String message) {
        super(message);
    }

    protected AuthorizationDomainException(DomainError error, String message) {
        super(error, message);
    }
}
