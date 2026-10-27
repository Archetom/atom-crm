package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CallerGuardTest {

    private final CallerGuard callerGuard = new CallerGuard();

    @Test
    void derivesTenantOnlyAfterTheCallerHasTheRequiredAuthority() {
        AuthenticatedCaller caller = new AuthenticatedCaller(7L, 11L, Set.of("users:read"));

        assertEquals(11L, callerGuard.requireTenant(caller, "users:read").getValue());
    }

    @Test
    void rejectsMissingCallerBeforeTenantExtraction() {
        NonRetryableApplicationException exception = assertThrows(
                NonRetryableApplicationException.class,
                () -> callerGuard.requireTenant(null, "users:read"));

        assertEquals(ApplicationErrorCode.AUTHENTICATION_REQUIRED, exception.getErrorCode());
    }

    @Test
    void rejectsInvalidRequiredAuthorityOrMissingCallerAuthorities() {
        AuthenticatedCaller caller = new AuthenticatedCaller(7L, 11L, Set.of("users:read"));
        AuthenticatedCaller callerWithoutAuthorities = new AuthenticatedCaller(7L, 11L, Set.of());

        assertAccessDenied(() -> callerGuard.requireTenant(caller, null));
        assertAccessDenied(() -> callerGuard.requireTenant(caller, "users:write"));
        assertAccessDenied(() -> callerGuard.requireTenant(caller, " "));
        assertAccessDenied(() -> callerGuard.requireTenant(callerWithoutAuthorities, "users:read"));
    }

    private void assertAccessDenied(Runnable action) {
        NonRetryableApplicationException exception = assertThrows(
                NonRetryableApplicationException.class,
                action::run);

        assertEquals(ApplicationErrorCode.ACCESS_DENIED, exception.getErrorCode());
    }
}
