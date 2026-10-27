package io.github.archetom.crm.infra.rest.util;

import io.github.archetom.crm.infra.rest.result.RestErrorResult;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.common.error.CommonError;
import io.github.archetom.common.result.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Rest result encapsulate
 *
 * @author hanfeng
 */
public final class ResponseEntityUtil {

    private ResponseEntityUtil() {
    }

    private static <T> ResponseEntity<T> success(Result<T> result) {
        return ResponseEntity.ok(result.getData());
    }

    private static ResponseEntity<?> fail(Result<?> result) {
        CommonError error = result.getErrorContext() == null
                ? null : result.getErrorContext().fetchRootError();

        RestErrorResult restResult = new RestErrorResult();
        if (error == null || error.getErrorCode() == null) {
            ApplicationErrorCode fallback = ApplicationErrorCode.UNKNOWN;
            restResult.setErrCode(fallback.getCompleteCode("9999"));
            restResult.setErrMsg(fallback.getDescription().trim());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(restResult);
        }

        restResult.setErrCode(error.getErrorCode().toString());
        restResult.setErrMsg(error.getErrorMsg());

        return ResponseEntity.status(resolveHttpStatus(error)).body(restResult);
    }

    public static ResponseEntity<?> assembleResponse(Result<?> result) {
        if (result.isSuccess()) {
            return ResponseEntityUtil.success(result);
        } else {
            return ResponseEntityUtil.fail(result);
        }
    }

    private static HttpStatus resolveHttpStatus(CommonError error) {
        ApplicationErrorCode errorCode = ApplicationErrorCode.fromCode(error.getErrorCode().getErrorSpecific());
        return switch (errorCode) {
            case PARAMETER_INVALID -> HttpStatus.BAD_REQUEST;
            case AUTHENTICATION_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case RESOURCE_NOT_FOUND, ENTERPRISE_DATA_NO_RESULT -> HttpStatus.NOT_FOUND;
            case VERSION_CONFLICT, CONCURRENT_OPERATION, RESOURCE_ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case ENTERPRISE_DATA_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case ENTERPRISE_DATA_UNAVAILABLE, CONNECTOR_UNAVAILABLE, ATTACHMENT_UNAVAILABLE,
                    AUTHENTICATION_DELIVERY_UNAVAILABLE
                    -> HttpStatus.SERVICE_UNAVAILABLE;
            case ATTACHMENT_RANGE_INVALID -> HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE;
            case UNKNOWN, SYSTEM -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }
}
