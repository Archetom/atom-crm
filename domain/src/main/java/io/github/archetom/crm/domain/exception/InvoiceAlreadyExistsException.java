package io.github.archetom.crm.domain.exception;

public final class InvoiceAlreadyExistsException extends InvoiceDomainException {
    public InvoiceAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "invoice number or unique field already exists");
    }
}
