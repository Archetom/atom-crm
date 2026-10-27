package io.github.archetom.crm.domain.exception;

/** Missing and out-of-scope contacts deliberately share one failure. */
public final class ContactNotFoundException extends ContactDomainException {

    public ContactNotFoundException() {
        super(DomainError.NOT_FOUND, "contact was not found");
    }
}
