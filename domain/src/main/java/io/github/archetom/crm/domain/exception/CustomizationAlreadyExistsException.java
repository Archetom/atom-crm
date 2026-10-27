package io.github.archetom.crm.domain.exception;

/** A tenant customization conflicts with an existing stable key or name. */
public final class CustomizationAlreadyExistsException extends CustomizationDomainException {

    public CustomizationAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "customization already exists");
    }
}
