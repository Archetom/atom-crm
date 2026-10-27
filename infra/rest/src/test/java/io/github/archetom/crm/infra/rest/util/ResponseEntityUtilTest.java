package io.github.archetom.crm.infra.rest.util;

import io.github.archetom.crm.infra.rest.result.RestErrorResult;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.ApplicationException;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ResponseEntityUtilTest {

    private static final String OPERATION_CODE = "7302";

    @ParameterizedTest
    @MethodSource("enterpriseDataErrors")
    void mapsEveryEnterpriseDataErrorToItsStableHttpContract(
            ApplicationErrorCode errorCode, HttpStatus expectedStatus) {
        Result<Object> result = ResultUtil.genErrorResult(
                new Result<>(), new ApplicationException(errorCode),
                OPERATION_CODE, "test-app");

        ResponseEntity<?> response = ResponseEntityUtil.assembleResponse(result);

        assertEquals(expectedStatus, response.getStatusCode());
        RestErrorResult error = assertInstanceOf(RestErrorResult.class, response.getBody());
        assertEquals(errorCode.getCompleteCode(OPERATION_CODE), error.getErrCode());
        assertEquals(errorCode.getDescription(), error.getErrMsg());
    }

    private static Stream<Arguments> enterpriseDataErrors() {
        return Stream.of(
                Arguments.of(ApplicationErrorCode.ENTERPRISE_DATA_NO_RESULT,
                        HttpStatus.NOT_FOUND),
                Arguments.of(ApplicationErrorCode.ENTERPRISE_DATA_LIMITED,
                        HttpStatus.TOO_MANY_REQUESTS),
                Arguments.of(ApplicationErrorCode.ENTERPRISE_DATA_UNAVAILABLE,
                        HttpStatus.SERVICE_UNAVAILABLE),
                Arguments.of(ApplicationErrorCode.ENTERPRISE_DATA_UNSUPPORTED,
                        HttpStatus.UNPROCESSABLE_CONTENT));
    }
}
