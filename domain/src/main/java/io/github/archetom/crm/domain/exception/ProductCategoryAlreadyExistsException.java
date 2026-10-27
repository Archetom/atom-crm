package io.github.archetom.crm.domain.exception;

public class ProductCategoryAlreadyExistsException extends ProductDomainException {

    public ProductCategoryAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS,
                "a product category with this name already exists under the same parent");
    }
}
