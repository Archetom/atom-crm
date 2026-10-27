package io.github.archetom.crm.domain.exception;

public class ActivityNotFoundException extends DomainException {
    public ActivityNotFoundException() {
        super(DomainError.NOT_FOUND, "activity was not found");
    }
}
