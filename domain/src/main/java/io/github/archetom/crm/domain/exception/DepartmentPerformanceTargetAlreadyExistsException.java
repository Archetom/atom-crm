package io.github.archetom.crm.domain.exception;

/** Raised when one department metric period is configured more than once. */
public final class DepartmentPerformanceTargetAlreadyExistsException extends PerformanceTargetDomainException {
    public DepartmentPerformanceTargetAlreadyExistsException() {
        super(DomainError.ALREADY_EXISTS, "department performance target already exists");
    }
}
