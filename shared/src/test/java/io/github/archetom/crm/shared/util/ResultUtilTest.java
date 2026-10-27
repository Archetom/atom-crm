package io.github.archetom.crm.shared.util;

import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.ApplicationException;
import io.github.archetom.common.error.CommonError;
import io.github.archetom.common.error.ErrorCode;
import io.github.archetom.common.error.ErrorContext;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultUtilTest {

    private static final String EVENT_CODE = "1001";
    private static final String APP_NAME = "shared-test";

    @Test
    void mapsSuccessfulDataWithoutChangingTheResultStatus() {
        Result<Integer> source = new Result<>();
        source.setSuccess(true);
        source.setData(42);

        Result<String> mapped = ResultUtil.map(source, Object::toString);

        assertTrue(mapped.isSuccess());
        assertEquals("42", mapped.getData());
    }

    @Test
    void preservesTheExistingErrorContextWhenMappingAFailure() {
        ErrorContext errorContext = new ErrorContext();
        Result<Integer> source = new Result<>();
        source.setSuccess(false);
        source.setErrorContext(errorContext);

        Result<String> mapped = ResultUtil.map(source, Object::toString);

        assertFalse(mapped.isSuccess());
        assertSame(errorContext, mapped.getErrorContext());
    }

    @Test
    void createsGenericPublicErrorForUnexpectedFailures() {
        Result<Void> result = ResultUtil.genErrorResult(
                new IllegalStateException("persistence driver failed"), APP_NAME);

        CommonError rootError = result.getErrorContext().fetchRootError();
        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrorContext().getErrorStack().size());
        assertEquals(ApplicationErrorCode.UNKNOWN.getCompleteCode("9999"), rootError.getErrorCode().toString());
        assertEquals(ApplicationErrorCode.UNKNOWN.getDescription(), rootError.getErrorMsg());
        assertEquals(APP_NAME, rootError.getLocation());
    }

    @Test
    void hidesRequestedMessagesForInternalErrorCategories() {
        String internalDetail = "persistence driver failed";

        assertEquals(ApplicationErrorCode.UNKNOWN.getDescription(),
                ResultUtil.publicMessage(ApplicationErrorCode.UNKNOWN, internalDetail));
        assertEquals(ApplicationErrorCode.SYSTEM.getDescription(),
                ResultUtil.publicMessage(ApplicationErrorCode.SYSTEM, internalDetail));
    }

    @Test
    void retainsExplicitSafeMessagesForBusinessRejections() {
        Result<Void> result = new Result<>();
        ApplicationException exception = new ApplicationException(
                ApplicationErrorCode.PARAMETER_INVALID,
                "Email address is invalid");

        ResultUtil.genErrorResult(result, exception, EVENT_CODE, APP_NAME);

        CommonError rootError = result.getErrorContext().fetchRootError();
        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrorContext().getErrorStack().size());
        assertEquals("Email address is invalid", rootError.getErrorMsg());
        assertEquals("DE0311001101", rootError.getErrorCode().toString());
        assertEquals(ApplicationErrorCode.PARAMETER_INVALID.getCompleteCode(EVENT_CODE),
                rootError.getErrorCode().toString());
    }

    @Test
    void appendsApplicationErrorToItsExistingErrorContext() {
        ErrorContext originalContext = ErrorUtil.makeAndAddError(
                new ErrorCode(ApplicationErrorCode.PARAMETER_INVALID.getCompleteCode(EVENT_CODE),
                        ApplicationErrorCode.VERSION),
                "Original validation error", "upstream");
        ApplicationException exception = new ApplicationException(ApplicationErrorCode.SYSTEM, originalContext);

        Result<Void> result = ResultUtil.genErrorResult(new Result<>(), exception, EVENT_CODE, APP_NAME);

        CommonError rootError = result.getErrorContext().fetchRootError();
        CommonError currentError = result.getErrorContext().fetchCurrentError();
        assertSame(originalContext, result.getErrorContext());
        assertEquals(2, result.getErrorContext().getErrorStack().size());
        assertEquals(ApplicationErrorCode.PARAMETER_INVALID.getCompleteCode(EVENT_CODE),
                rootError.getErrorCode().toString());
        assertEquals("Original validation error", rootError.getErrorMsg());
        assertEquals(ApplicationErrorCode.SYSTEM.getCompleteCode(EVENT_CODE), currentError.getErrorCode().toString());
        assertEquals(ApplicationErrorCode.SYSTEM.getDescription(), currentError.getErrorMsg());
        assertEquals(APP_NAME, currentError.getLocation());
    }

    @Test
    void rejectsMissingResultOrExceptionAndInvalidEventCodes() {
        ApplicationException exception = new ApplicationException(
                ApplicationErrorCode.PARAMETER_INVALID, "Invalid input");

        assertThrows(NullPointerException.class,
                () -> ResultUtil.genErrorResult(null, exception, EVENT_CODE, APP_NAME));
        assertThrows(NullPointerException.class,
                () -> ResultUtil.genErrorResult(new Result<>(), null, EVENT_CODE, APP_NAME));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ResultUtil.genErrorResult(new Result<>(), exception, "123", APP_NAME));

        assertEquals("eventCode must be exactly 4 characters long", failure.getMessage());
    }
}
