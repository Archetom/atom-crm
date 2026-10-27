package io.github.archetom.crm.shared.util;

import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.ApplicationException;
import io.github.archetom.common.result.Result;
import io.github.archetom.common.error.ErrorCode;
import io.github.archetom.common.error.ErrorContext;

import java.util.Objects;
import java.util.function.Function;

/** Creates public results while preventing internal error details from leaking. */
public final class ResultUtil {

    private ResultUtil() {
    }

    /** Maps successful data while preserving an existing public error unchanged. */
    public static <S, T> Result<T> map(Result<S> source, Function<? super S, ? extends T> mapper) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(mapper, "mapper must not be null");

        Result<T> result = new Result<>();
        result.setSuccess(source.isSuccess());
        if (source.isSuccess()) {
            result.setData(mapper.apply(source.getData()));
        } else {
            result.setErrorContext(source.getErrorContext());
        }
        return result;
    }

    /**
     * generate error result.
     *
     * @param ex exception
     * @param appName application
     * @return error result
     */
    public static <T> Result<T> genErrorResult(Throwable ex, String appName) {

        final Result<T> result = new Result<>();

        result.setSuccess(false);

        ApplicationErrorCode errorCodeEnum = ApplicationErrorCode.UNKNOWN;

        ErrorContext errorContext = ErrorUtil.makeAndAddError(
                new ErrorCode(errorCodeEnum.getCompleteCode("9999"), ApplicationErrorCode.VERSION),
                publicMessage(errorCodeEnum, null), appName);

        result.setErrorContext(errorContext);
        return result;
    }

    /**
     * based on exception generate error context add to result in
     *
     * @param result query result
     * @param e exception
     * @return encapsulate of result
     */
    public static <T> Result<T> genErrorResult(Result<T> result, ApplicationException e,
                                               String eventCode, String appName) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(e, "exception must not be null");
        requireEventCode(eventCode);

        result.setSuccess(false);
        ErrorContext errorContext = ErrorUtil.makeAndAddError(
                e.getErrorContext(),
                new ErrorCode(e.getErrorCode().getCompleteCode(eventCode), ApplicationErrorCode.VERSION),
                publicMessage(e.getErrorCode(), e.getMessage()), appName);
        result.setErrorContext(errorContext);
        return result;
    }

    /**
     * Resolve the client-facing message without exposing internal exception details.
     */
    public static String publicMessage(ApplicationErrorCode errorCode, String requestedMessage) {
        if (errorCode == null || errorCode.isInternalError() || requestedMessage == null || requestedMessage.isBlank()) {
            ApplicationErrorCode safeErrorCode = errorCode == null ? ApplicationErrorCode.UNKNOWN : errorCode;
            return safeErrorCode.getDescription().trim();
        }
        return requestedMessage;
    }

    private static void requireEventCode(String eventCode) {
        if (eventCode == null || eventCode.length() != 4) {
            throw new IllegalArgumentException("eventCode must be exactly 4 characters long");
        }
    }
}
