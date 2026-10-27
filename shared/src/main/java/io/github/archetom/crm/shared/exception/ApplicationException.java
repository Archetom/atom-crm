package io.github.archetom.crm.shared.exception;


import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.common.error.ErrorContext;

import java.io.Serial;
import java.util.Objects;

/**
 * Application-layer failure with a stable public error code.
 */
public class ApplicationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 4718379562894293206L;

    /**
     * exception error code
     */
    private final ApplicationErrorCode errorCode;

    /**
     * error context
     */
    private final ErrorContext errorContext;

    /** Create an application failure from an existing error context. */
    @SuppressWarnings("unused")
    public ApplicationException(ApplicationErrorCode errorCode, ErrorContext errorContext) {
        super(requireErrorCode(errorCode).getDescription());
        this.errorCode = requireErrorCode(errorCode);
        this.errorContext = errorContext;
    }

    /** Create an application failure with a safe public message. */
    public ApplicationException(ApplicationErrorCode errorCode, String message) {
        super(message);
        this.errorCode = requireErrorCode(errorCode);
        this.errorContext = null;
    }

    /** Create an application failure while retaining its internal cause. */
    public ApplicationException(ApplicationErrorCode errorCode, Throwable originalThrowable) {
        super(requireErrorCode(errorCode).getDescription(), originalThrowable);
        this.errorCode = requireErrorCode(errorCode);
        this.errorContext = null;
    }

    /** Create an application failure with the error code's default message. */
    public ApplicationException(ApplicationErrorCode errorCode) {
        super(requireErrorCode(errorCode).getDescription());
        this.errorCode = requireErrorCode(errorCode);
        this.errorContext = null;
    }

    /** Create an application failure with a safe message and internal cause. */
    public ApplicationException(ApplicationErrorCode errorCode, String message, Throwable e) {
        super(message, e);
        this.errorCode = requireErrorCode(errorCode);
        this.errorContext = null;
    }

    public ApplicationErrorCode getErrorCode() {
        return errorCode;
    }

    public ErrorContext getErrorContext() {
        return errorContext;
    }

    private static ApplicationErrorCode requireErrorCode(ApplicationErrorCode errorCode) {
        return Objects.requireNonNull(errorCode, "errorCode must not be null");
    }
}
