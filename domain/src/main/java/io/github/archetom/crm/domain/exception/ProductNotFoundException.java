package io.github.archetom.crm.domain.exception;

public class ProductNotFoundException extends ProductDomainException {

    public ProductNotFoundException() {
        super(DomainError.NOT_FOUND, "product was not found");
    }
}
