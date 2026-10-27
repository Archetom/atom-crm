package io.github.archetom.crm.domain.exception;

/** Raised when the target's tenant/member/month natural key already exists. */
public final class PerformanceTargetAlreadyExistsException extends PerformanceTargetDomainException {
    public PerformanceTargetAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "performance target already exists");
    }
}
