package io.github.archetom.crm.domain.exception;

public final class InvoiceNotFoundException extends InvoiceDomainException {
    public InvoiceNotFoundException() {
        super(DomainError.NOT_FOUND, "invoice was not found");
    }
}
