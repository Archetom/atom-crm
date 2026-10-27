package io.github.archetom.crm.domain.exception;

public class WorkTaskDomainException extends DomainException {

    public WorkTaskDomainException(String message) {
        super(message);
    }

    protected WorkTaskDomainException(DomainError error, String message) {
        super(error, message);
    }
}
