package io.github.archetom.crm.domain.exception;

public class InvoiceDomainException extends DomainException {
    public InvoiceDomainException(String message) {
        super(message);
    }

    protected InvoiceDomainException(DomainError error, String message) {
        super(error, message);
    }
}
