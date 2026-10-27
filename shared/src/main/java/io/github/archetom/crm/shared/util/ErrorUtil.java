package io.github.archetom.crm.shared.util;

import io.github.archetom.common.error.CommonError;
import io.github.archetom.common.error.ErrorCode;
import io.github.archetom.common.error.ErrorContext;

import lombok.extern.slf4j.Slf4j;

/** Builds the error primitives used by the public result contract. */
@Slf4j
public final class ErrorUtil {

    private ErrorUtil() {
    }

    /** Build an error code from its stable components. */
    public static ErrorCode makeErrorCode(String errorLevel, String errorType, String errorScene, String errorSpecific) {
        return new ErrorCode(errorLevel, errorType, errorScene, errorSpecific);
    }

    /**
     * error
     *
     * @param errorCode error code
     * @param message error message
     * @return error
     */
    private static CommonError makeError(ErrorCode errorCode, String message) {
        CommonError error = new CommonError();
        error.setErrorCode(errorCode);
        error.setErrorMsg(message);
        return error;
    }

    /**
     * error
     *
     * @param errorCode error code
     * @param message error message
     * @param location error position
     * @return error
     */
    private static CommonError makeError(ErrorCode errorCode, String message, String location) {
        CommonError error = new CommonError();
        error.setLocation(location);
        error.setErrorCode(errorCode);
        error.setErrorMsg(message);
        return error;
    }

    /** Create a new context containing one error. */
    private static ErrorContext addError(CommonError error) {
        return addError(null, error);
    }

    /** Add an error to an existing context or create a new context. */
    private static ErrorContext addError(ErrorContext context, CommonError error) {
        if (context == null) {
            context = new ErrorContext();
        }
        if (error == null) {
            log.error("Cannot add a null error to the error context");
            return context;
        }

        context.addError(error);

        return context;
    }

    /**
     * error error context
     *
     * @param context error context
     * @param errorCode error code
     * @param message error message
     * @return of error context
     */
    public static ErrorContext makeAndAddError(ErrorContext context, ErrorCode errorCode, String message) {
        CommonError error = makeError(errorCode, message);
        context = addError(context, error);

        return context;
    }

    /** Add a located error to an existing context or create a new context. */
    public static ErrorContext makeAndAddError(ErrorContext context, ErrorCode errorCode, String message,
                                               String location) {
        CommonError error = makeError(errorCode, message, location);
        return addError(context, error);
    }

    /**
     * error error context
     *
     * @param errorCode error code
     * @param message error message
     * @param location exception of application
     * @return of error context
     */
    public static ErrorContext makeAndAddError(ErrorCode errorCode, String message, String location) {
        CommonError error = makeError(errorCode, message, location);
        return addError(error);
    }
}
