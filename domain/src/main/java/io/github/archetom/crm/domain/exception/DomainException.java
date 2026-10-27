package io.github.archetom.crm.domain.exception;

import java.io.Serial;

/**
 * Framework-neutral domain failure.
 */
public abstract class DomainException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final DomainError error;

    protected DomainException(String message) {
        this(DomainError.RULE_VIOLATION, message);
    }

    protected DomainException(String message, Throwable cause) {
        this(DomainError.RULE_VIOLATION, message, cause);
    }

    protected DomainException(DomainError error, String message) {
        super(message);
        this.error = requireError(error);
    }

    protected DomainException(DomainError error, String message, Throwable cause) {
        super(message, cause);
        this.error = requireError(error);
    }

    public DomainError getError() {
        return error;
    }

    private static DomainError requireError(DomainError error) {
        if (error == null) {
            throw new IllegalArgumentException("domain error must not be null");
        }
        return error;
    }
}
