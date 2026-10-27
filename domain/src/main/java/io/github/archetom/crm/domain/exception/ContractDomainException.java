package io.github.archetom.crm.domain.exception;

public class ContractDomainException extends DomainException {
    public ContractDomainException(String message) {
        super(message);
    }

    protected ContractDomainException(DomainError error, String message) {
        super(error, message);
    }
}
