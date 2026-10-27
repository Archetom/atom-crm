package io.github.archetom.crm.domain.exception;

/** Domain rejection for attachment aggregate transitions. */
public class AttachmentDomainException extends DomainException {

    public AttachmentDomainException(String message) {
        super(message);
    }
}
