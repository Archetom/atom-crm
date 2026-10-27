package io.github.archetom.crm.domain.exception;

public final class WorkTaskNotFoundException extends WorkTaskDomainException {

    public WorkTaskNotFoundException() {
        super(DomainError.NOT_FOUND, "work task was not found");
    }
}
