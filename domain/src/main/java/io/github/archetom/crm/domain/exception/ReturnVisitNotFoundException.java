package io.github.archetom.crm.domain.exception;

public class ReturnVisitNotFoundException extends DomainException {
    public ReturnVisitNotFoundException() {
        super(DomainError.NOT_FOUND, "return visit was not found");
    }
}
