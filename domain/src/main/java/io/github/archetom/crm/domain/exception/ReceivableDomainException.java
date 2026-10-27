package io.github.archetom.crm.domain.exception;

public class ReceivableDomainException extends DomainException {
    public ReceivableDomainException(String message) {
        super(message);
    }

    protected ReceivableDomainException(DomainError error, String message) {
        super(error, message);
    }
}
