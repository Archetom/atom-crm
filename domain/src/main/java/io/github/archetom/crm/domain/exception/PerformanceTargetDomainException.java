package io.github.archetom.crm.domain.exception;

/** Rejects an invalid, tenant-owned performance target aggregate state. */
public class PerformanceTargetDomainException extends DomainException {
    public PerformanceTargetDomainException(String message) {
        super(message);
    }

    protected PerformanceTargetDomainException(DomainError error, String message) {
        super(error, message);
    }
}
