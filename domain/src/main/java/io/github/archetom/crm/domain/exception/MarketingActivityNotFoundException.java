package io.github.archetom.crm.domain.exception;

public class MarketingActivityNotFoundException extends DomainException {
    public MarketingActivityNotFoundException() {
        super(DomainError.NOT_FOUND, "marketing activity was not found");
    }
}
