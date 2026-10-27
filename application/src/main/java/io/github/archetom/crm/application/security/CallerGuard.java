package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.springframework.stereotype.Component;

/** Central application-layer guard for caller authority and tenant extraction. */
@Component
public class CallerGuard {

    public TenantId requireTenant(AuthenticatedCaller caller, String authority) {
        if (caller == null) {
            throw new NonRetryableApplicationException(
                    ApplicationErrorCode.AUTHENTICATION_REQUIRED,
                    "Authenticated caller is required");
        }
        if (authority == null || authority.isBlank() || !caller.hasAuthority(authority)) {
            throw new NonRetryableApplicationException(
                    ApplicationErrorCode.ACCESS_DENIED,
                    "Caller does not have the required authority");
        }
        return new TenantId(caller.tenantId());
    }
}
