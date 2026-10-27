package io.github.archetom.crm.domain.exception;

public final class ReceivableNotFoundException extends ReceivableDomainException {
    public ReceivableNotFoundException() {
        super(DomainError.NOT_FOUND, "receivable was not found");
    }
}
