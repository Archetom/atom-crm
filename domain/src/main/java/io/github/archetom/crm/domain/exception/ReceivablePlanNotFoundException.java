package io.github.archetom.crm.domain.exception;

public final class ReceivablePlanNotFoundException extends ReceivableDomainException {
    public ReceivablePlanNotFoundException() {
        super(DomainError.NOT_FOUND, "receivable plan was not found");
    }
}
