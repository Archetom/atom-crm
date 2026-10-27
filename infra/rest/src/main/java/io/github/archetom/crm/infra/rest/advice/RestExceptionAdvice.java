package io.github.archetom.crm.infra.rest.advice;

import io.github.archetom.crm.application.exception.DomainExceptionMapper;
import io.github.archetom.crm.domain.exception.DomainException;
import io.github.archetom.crm.infra.rest.util.ErrorResultWrapUtil;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.ApplicationException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps transport, application, and domain exceptions to stable HTTP error responses.
 */
@Slf4j
@RestControllerAdvice
public class RestExceptionAdvice {

    private final String appName;

    public RestExceptionAdvice(@Value("${spring.application.name}") String appName) {
        this.appName = appName;
    }

    /** Maps request-body validation failures to a safe parameter error. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validationBodyException(MethodArgumentNotValidException exception) {
        return ResponseEntityUtil.assembleResponse(ErrorResultWrapUtil.genErrorResultValidation(exception, appName));
    }

    /** Maps malformed request bodies without logging their potentially sensitive contents. */
    @ExceptionHandler(HttpMessageConversionException.class)
    public ResponseEntity<?> parameterTypeException(HttpMessageConversionException exception) {
        log.warn("Request body conversion failed: exceptionType={}", exception.getClass().getName());
        return ResponseEntityUtil.assembleResponse(ErrorResultWrapUtil.genErrorResultValidation(exception, appName));
    }

    /** Maps request-parameter binding and constraint failures. */
    @ExceptionHandler({
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<?> requestParameterException(Exception exception) {
        log.warn("Request parameter validation failed: {}", exception.getClass().getSimpleName());
        return ResponseEntityUtil.assembleResponse(ErrorResultWrapUtil.genErrorResult(
                ApplicationErrorCode.PARAMETER_INVALID, "Request parameters are invalid", appName));
    }

    /** Maps explicit boundary authorization rejection to HTTP 403. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> accessDeniedException(AccessDeniedException exception) {
        log.warn("Request access denied: exceptionType={}", exception.getClass().getName());
        return ResponseEntityUtil.assembleResponse(ErrorResultWrapUtil.genErrorResult(
                ApplicationErrorCode.ACCESS_DENIED,
                ApplicationErrorCode.ACCESS_DENIED.getDescription(), appName));
    }

    /** Maps invalid value-object and boundary arguments without exposing rejected values. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> illegalArgumentException(IllegalArgumentException exception) {
        log.warn("Request argument rejected: exceptionType={}", exception.getClass().getName());
        return ResponseEntityUtil.assembleResponse(ErrorResultWrapUtil.genErrorResult(
                ApplicationErrorCode.PARAMETER_INVALID,
                ApplicationErrorCode.PARAMETER_INVALID.getDescription(), appName));
    }

    /** Missing framework routes/resources use the public not-found contract, without request paths. */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<?> resourceNotFoundException(Exception exception) {
        return ResponseEntityUtil.assembleResponse(ErrorResultWrapUtil.genErrorResult(
                ApplicationErrorCode.RESOURCE_NOT_FOUND,
                ApplicationErrorCode.RESOURCE_NOT_FOUND.getDescription(), appName));
    }

    /** Preserves explicit codes for domain rules, missing resources, and duplicates. */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<?> domainException(DomainException exception) {
        ApplicationErrorCode errorCode = DomainExceptionMapper.toApplicationCode(exception);
        log.warn("Domain request rejected: error={}", exception.getError());
        return ResponseEntityUtil.assembleResponse(ErrorResultWrapUtil.genErrorResult(
                errorCode, exception.getMessage(), appName));
    }

    /** Maps an expected application-layer rejection. */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<?> applicationException(ApplicationException exception) {
        log.warn("Application request rejected: code={}", exception.getErrorCode());
        return ResponseEntityUtil.assembleResponse(ErrorResultWrapUtil.genErrorResult(
                exception.getErrorCode(), exception.getMessage(), appName));
    }

    /** Preserves a completed use case's operation-scoped public error unchanged. */
    @ExceptionHandler(ResultResponseException.class)
    public ResponseEntity<?> resultResponseException(ResultResponseException exception) {
        log.warn("Application result rejected at REST boundary");
        return ResponseEntityUtil.assembleResponse(exception.result());
    }

    /** Maps an unexpected failure without exposing its message or cause. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpectedException(Exception exception) {
        log.error("Unexpected request failure: exceptionType={}", exception.getClass().getName());
        return ResponseEntityUtil.assembleResponse(ErrorResultWrapUtil.genErrorResult(
                ApplicationErrorCode.UNKNOWN, null, appName));
    }
}
