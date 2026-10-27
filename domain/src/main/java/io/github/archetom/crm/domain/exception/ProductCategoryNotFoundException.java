package io.github.archetom.crm.domain.exception;

public class ProductCategoryNotFoundException extends ProductDomainException {

    public ProductCategoryNotFoundException() {
        super(DomainError.NOT_FOUND, "product category was not found");
    }
}
