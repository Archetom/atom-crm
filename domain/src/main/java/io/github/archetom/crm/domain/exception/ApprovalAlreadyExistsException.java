package io.github.archetom.crm.domain.exception;

public final class ApprovalAlreadyExistsException extends ApprovalDomainException {
    public ApprovalAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "approval resource already exists");
    }
}
