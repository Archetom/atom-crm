package io.github.archetom.crm.domain.exception;

public final class ContractAlreadyExistsException extends ContractDomainException {
    public ContractAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "contract number or unique field already exists");
    }
}
