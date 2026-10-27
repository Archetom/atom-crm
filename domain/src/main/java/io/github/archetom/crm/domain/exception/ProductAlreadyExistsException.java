package io.github.archetom.crm.domain.exception;

public class ProductAlreadyExistsException extends ProductDomainException {

    public ProductAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS,
                "product code or custom unique value already exists");
    }
}
