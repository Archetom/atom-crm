package io.github.archetom.crm.domain.exception;

/** Stable approval-domain invariant violation. */
public class ApprovalDomainException extends DomainException {
    public ApprovalDomainException(String message) {
        super(message);
    }

    protected ApprovalDomainException(DomainError error, String message) {
        super(error, message);
    }
}
