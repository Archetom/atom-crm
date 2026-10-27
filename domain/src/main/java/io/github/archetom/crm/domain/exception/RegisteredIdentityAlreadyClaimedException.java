package io.github.archetom.crm.domain.exception;

/** Safe tenant-local conflict; it never carries the identity value or occupying Customer. */
public final class RegisteredIdentityAlreadyClaimedException extends DomainException {
    public RegisteredIdentityAlreadyClaimedException() {
        super("registered identity is already claimed");
    }
}
