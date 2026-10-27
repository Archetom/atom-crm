package io.github.archetom.crm.domain.exception;

public final class ContractNotFoundException extends ContractDomainException {
    public ContractNotFoundException() {
        super(DomainError.NOT_FOUND, "contract was not found");
    }
}
