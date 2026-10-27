package io.github.archetom.crm.application.service.template;

import io.github.archetom.crm.application.exception.DomainExceptionMapper;
import io.github.archetom.crm.domain.exception.DomainException;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.ApplicationException;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.crm.shared.operation.OperationCode;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;

import java.util.Objects;

/**
 * Shared result and exception policy for application operation templates.
 */
@Slf4j
abstract class OperationTemplateSupport {

    private final String appName;

    protected OperationTemplateSupport(String appName) {
        this.appName = Objects.requireNonNull(appName, "appName must not be null");
    }

    public final <T> Result<T> execute(OperationCode event, ServiceOperation<T> operation) {
        return execute(event, operation, OperationLogPolicy.DEFAULT);
    }

    public final <T> Result<T> execute(OperationCode event, ServiceOperation<T> operation,
                                       OperationLogPolicy logPolicy) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(logPolicy, "logPolicy must not be null");

        Result<T> result = new Result<>();
        try {
            operation.validate();
            operation.prepare();
            T data = invoke(operation);
            result.setData(data);
            result.setSuccess(true);
            return result;
        } catch (DomainException exception) {
            if (logPolicy == OperationLogPolicy.SAFE_BACKGROUND) {
                log.warn("Background operation rejected: event={}", event);
            } else {
                log.warn("Domain operation rejected: event={}, error={}", event, exception.getError());
            }
            NonRetryableApplicationException mapped = new NonRetryableApplicationException(
                    DomainExceptionMapper.toApplicationCode(exception),
                    exception.getMessage(),
                    exception);
            return ResultUtil.genErrorResult(result, mapped, event.code(), appName);
        } catch (IllegalArgumentException exception) {
            if (logPolicy == OperationLogPolicy.SAFE_BACKGROUND) {
                log.warn("Background operation rejected: event={}", event);
            } else {
                log.warn("Application input rejected: event={}, exceptionType={}",
                        event, exception.getClass().getName());
            }
            NonRetryableApplicationException mapped = new NonRetryableApplicationException(
                    ApplicationErrorCode.PARAMETER_INVALID,
                    ApplicationErrorCode.PARAMETER_INVALID.getDescription(),
                    exception);
            return ResultUtil.genErrorResult(result, mapped, event.code(), appName);
        } catch (NonRetryableApplicationException exception) {
            log.warn("Application operation rejected: event={}, error={}",
                    event, exception.getErrorCode());
            return ResultUtil.genErrorResult(result, exception, event.code(), appName);
        } catch (ApplicationException exception) {
            log.warn("Application operation failed: event={}, error={}",
                    event, exception.getErrorCode());
            return ResultUtil.genErrorResult(result, exception, event.code(), appName);
        } catch (PessimisticLockingFailureException exception) {
            log.warn("Concurrent application operation timed out: event={}", event);
            ApplicationException mapped = new ApplicationException(
                    ApplicationErrorCode.CONCURRENT_OPERATION,
                    ApplicationErrorCode.CONCURRENT_OPERATION.getDescription(), exception);
            return ResultUtil.genErrorResult(result, mapped, event.code(), appName);
        } catch (RuntimeException exception) {
            if (logPolicy == OperationLogPolicy.SAFE_BACKGROUND) {
                log.warn("Background operation failed: event={}", event);
            } else {
                log.error("Unexpected application failure: event={}, exceptionType={}",
                        event, exception.getClass().getName());
            }
            return ResultUtil.genErrorResult(exception, appName);
        } finally {
            log.info("Application operation completed: event={}, success={}",
                    event, result.isSuccess());
        }
    }

    /**
     * Maps a rejection that is deliberately raised before an operation template invokes its
     * transaction. The operation code is retained so callers receive the use-case-specific
     * error contract rather than the generic unexpected-error scene.
     */
    protected final <T> Result<T> rejected(OperationCode event, ApplicationException exception) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(exception, "exception must not be null");
        Result<T> result = new Result<>();
        return ResultUtil.genErrorResult(result, exception, event.code(), appName);
    }

    protected <T> T invoke(ServiceOperation<T> operation) {
        T data = operation.execute();
        operation.onSuccess(data);
        return data;
    }
}
