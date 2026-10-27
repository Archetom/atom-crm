package io.github.archetom.crm.shared.exception;

import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.common.error.ErrorContext;

import java.io.Serial;

/** Stable application rejection that callers must not retry unchanged. */
public class NonRetryableApplicationException extends ApplicationException {
    @Serial
    private static final long serialVersionUID = 6445665908006351745L;

    /** Create a non-retryable rejection from an existing error context. */
    public NonRetryableApplicationException(final ApplicationErrorCode errorCode, final ErrorContext errorContext) {
        super(errorCode, errorContext);
    }

    /** Create a non-retryable rejection with a safe public message. */
    public NonRetryableApplicationException(final ApplicationErrorCode errorCode, final String msg) {
        super(errorCode, msg);
    }

    /**
     * Create a non-retryable exception while retaining its internal cause for server-side logs.
     *
     * @param errorCode exception code
     * @param msg safe public message
     * @param cause internal cause
     */
    public NonRetryableApplicationException(final ApplicationErrorCode errorCode, final String msg, final Throwable cause) {
        super(errorCode, msg, cause);
    }

    /** Create a non-retryable rejection with the default public message. */
    public NonRetryableApplicationException(final ApplicationErrorCode errorCode) {
        super(errorCode);
    }
}
