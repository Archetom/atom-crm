package io.github.archetom.crm.infra.rest.util;

import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.util.ErrorUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import io.github.archetom.common.error.ErrorCode;
import io.github.archetom.common.error.ErrorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

/** Converts HTTP binding and validation failures to the stable public error contract. */
@Slf4j
public final class ErrorResultWrapUtil {

    private static final String ERROR_SCENE = "9999";

    private ErrorResultWrapUtil() {
    }

    /** Generate an error result for controller binding or validation failures. */
    public static <T> Result<T> genErrorResultValidation(Object ex, String appName) {
        String message = "Request validation failed";
        String fieldName = "";
        if (ex instanceof MethodArgumentNotValidException validationException) {
            BindingResult bindingResult = validationException.getBindingResult();
            if (bindingResult.hasErrors()) {
                List<ObjectError> errors = bindingResult.getAllErrors();
                errors.stream()
                        .filter(FieldError.class::isInstance)
                        .map(FieldError.class::cast)
                        .forEach(fieldError -> log.warn(
                                "Data check failure: object={}, field={}, message={}",
                                fieldError.getObjectName(), fieldError.getField(), fieldError.getDefaultMessage()));
                if (!errors.isEmpty()) {
                    ObjectError firstError = errors.get(0);
                    if (firstError.getDefaultMessage() != null) {
                        message = firstError.getDefaultMessage();
                    }
                    if (firstError instanceof FieldError fieldError) {
                        fieldName = fieldError.getField();
                    }
                }
            }
        }

        if (ex instanceof HttpMessageConversionException) {
            message = "Request body is malformed";
        }

        String detail = fieldName.isBlank() ? message : fieldName + ": " + message;
        return genErrorResult(ApplicationErrorCode.PARAMETER_INVALID, detail, appName);
    }

    /**
     * Build a compatible {@link Result} with a stable public error message.
     */
    public static <T> Result<T> genErrorResult(ApplicationErrorCode errorCode, String message, String appName) {

        final Result<T> result = new Result<>();
        result.setSuccess(false);

        ErrorContext errorContext = ErrorUtil.makeAndAddError(
                new ErrorCode(errorCode.getCompleteCode(ERROR_SCENE), ApplicationErrorCode.VERSION),
                ResultUtil.publicMessage(errorCode, message), appName);

        result.setErrorContext(errorContext);
        return result;
    }
}
