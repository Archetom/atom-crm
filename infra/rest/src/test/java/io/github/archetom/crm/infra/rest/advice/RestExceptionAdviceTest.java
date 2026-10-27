package io.github.archetom.crm.infra.rest.advice;

import io.github.archetom.crm.domain.exception.UserAlreadyExistsException;
import io.github.archetom.crm.domain.exception.UserDomainException;
import io.github.archetom.crm.domain.exception.UserNotFoundException;
import io.github.archetom.crm.infra.rest.result.RestErrorResult;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.ApplicationException;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class RestExceptionAdviceTest {

    private final RestExceptionAdvice advice = new RestExceptionAdvice("test-app");

    @Test
    void shouldMapParameterErrorToBadRequest() {
        ResponseEntity<?> response = advice.applicationException(
                new ApplicationException(ApplicationErrorCode.PARAMETER_INVALID, "User ID must be positive"));

        assertError(response, HttpStatus.BAD_REQUEST, "101", "User ID must be positive");
    }

    @Test
    void shouldMapAuthenticationAndAuthorizationErrors() {
        ResponseEntity<?> unauthenticated = advice.applicationException(
                new ApplicationException(ApplicationErrorCode.AUTHENTICATION_REQUIRED));
        ResponseEntity<?> forbidden = advice.applicationException(
                new ApplicationException(ApplicationErrorCode.ACCESS_DENIED));

        assertError(unauthenticated, HttpStatus.UNAUTHORIZED, "102", "Authentication is required");
        assertError(forbidden, HttpStatus.FORBIDDEN, "103", "Access is denied");
    }

    @Test
    void shouldMapConnectorAuthenticationStoreFailureToServiceUnavailable() {
        ResponseEntity<?> response = advice.applicationException(
                new ApplicationException(ApplicationErrorCode.CONNECTOR_UNAVAILABLE));

        assertError(response, HttpStatus.SERVICE_UNAVAILABLE, "308",
                "Connector authentication service is unavailable");
    }

    @Test
    void shouldMapBoundaryAccessDeniedToForbidden() {
        ResponseEntity<?> response = advice.accessDeniedException(
                new AccessDeniedException("internal authorization details"));

        assertError(response, HttpStatus.FORBIDDEN, "103", "Access is denied");
    }

    @Test
    void shouldMapIllegalArgumentToBadRequestWithoutLeakingValue(CapturedOutput output) {
        String rejectedValue = "invalid phone +secret";
        ResponseEntity<?> response = advice.illegalArgumentException(
                new IllegalArgumentException(rejectedValue));

        RestErrorResult error = assertError(response, HttpStatus.BAD_REQUEST, "101",
                "Request parameters are invalid");
        assertFalse(error.getErrMsg().contains(rejectedValue));
        assertFalse(output.getAll().contains(rejectedValue));
    }

    @Test
    void shouldMapNotFoundToNotFound() {
        ResponseEntity<?> response = advice.domainException(new UserNotFoundException(42L));

        assertError(response, HttpStatus.NOT_FOUND, "300", "User does not exist");
    }

    @Test
    void shouldResolveFrameworkNotFoundToSafe404WithoutLeakingRequestDetails(CapturedOutput output)
            throws ReflectiveOperationException {
        String privatePath = "/private/reset/synthetic-secret-token";
        String privateHeader = "synthetic-secret-credential";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", privateHeader);
        var resolver = new ExceptionHandlerMethodResolver(RestExceptionAdvice.class);

        for (Exception exception : List.of(
                new NoResourceFoundException(HttpMethod.GET, privatePath, privatePath),
                new NoHandlerFoundException("GET", privatePath, headers))) {
            // Resolve through MVC's annotation dispatcher so the generic 500 handler cannot mask this regression.
            var handler = resolver.resolveMethod(exception);
            assertNotNull(handler);
            ResponseEntity<?> response = (ResponseEntity<?>) handler.invoke(advice, exception);

            RestErrorResult error = assertError(response, HttpStatus.NOT_FOUND, "300",
                    "Requested resource was not found");
            assertFalse(error.getErrMsg().contains(privatePath));
            assertFalse(error.getErrMsg().contains(privateHeader));
        }
        assertFalse(output.getAll().contains(privatePath));
        assertFalse(output.getAll().contains(privateHeader));
    }

    @Test
    void shouldMapEmailDuplicateToConflictWithCorrectMessage() {
        ResponseEntity<?> response = advice.domainException(
                UserAlreadyExistsException.byEmail("person@example.com"));

        assertError(response, HttpStatus.CONFLICT, "302", "Email already exists");
    }

    @Test
    void shouldMapGenericDomainRuleToUnprocessableContent() {
        ResponseEntity<?> response = advice.domainException(
                new UserDomainException("Deleted users cannot change status"));

        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "303",
                "Deleted users cannot change status");
    }

    @Test
    void shouldRejectMalformedBodyWithoutLeakingParserMessage(CapturedOutput output) {
        String internalMessage = "Unexpected token near credential=secret";
        ResponseEntity<?> response = advice.parameterTypeException(
                new HttpMessageConversionException("conversion failed", new IllegalArgumentException(internalMessage)));

        RestErrorResult error = assertError(response, HttpStatus.BAD_REQUEST, "101",
                "Request body is malformed");
        assertFalse(error.getErrMsg().contains(internalMessage));
        assertFalse(output.getAll().contains(internalMessage));
    }

    @Test
    void shouldMapUnknownFailureToInternalServerErrorWithoutLeakingMessage(CapturedOutput output) {
        String internalMessage = "database password=secret";
        ResponseEntity<?> response = advice.unexpectedException(new RuntimeException(internalMessage));

        RestErrorResult error = assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, "000",
                ApplicationErrorCode.UNKNOWN.getDescription());
        assertFalse(error.getErrMsg().contains(internalMessage));
        assertFalse(output.getAll().contains(internalMessage));
    }

    @Test
    void shouldPreserveOperationSceneForCompletedApplicationResult() {
        Result<Object> result = ResultUtil.genErrorResult(new Result<>(),
                new ApplicationException(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                        "Customer export exceeds the row limit"),
                "6035", "test-app");

        ResponseEntity<?> response = advice.resultResponseException(
                new ResultResponseException(result));

        RestErrorResult error = assertError(response, HttpStatus.UNPROCESSABLE_CONTENT,
                "002", "Customer export exceeds the row limit");
        assertEquals(ApplicationErrorCode.OPERATION_NOT_ALLOWED.getCompleteCode("6035"),
                error.getErrCode());
    }

    private RestErrorResult assertError(
            ResponseEntity<?> response,
            HttpStatus expectedStatus,
            String expectedSpecificCode,
            String expectedMessage
    ) {
        assertEquals(expectedStatus, response.getStatusCode());
        RestErrorResult error = assertInstanceOf(RestErrorResult.class, response.getBody());
        assertTrue(error.getErrCode().endsWith(expectedSpecificCode));
        assertEquals(expectedMessage, error.getErrMsg());
        return error;
    }
}
