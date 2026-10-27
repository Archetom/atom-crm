package io.github.archetom.crm.domain.exception;

public final class StageWorkflowAlreadyExistsException extends StageWorkflowDomainException {
    public StageWorkflowAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "stage workflow resource already exists");
    }
}
