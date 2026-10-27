package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves OA read entry and the independent tenant-management widening flag. */
@Component
@RequiredArgsConstructor
public class OaRequestAccessResolver {

    private final OrganizationAccessGuard accessGuard;

    public OaRequestAccess access(AuthenticatedCaller caller) {
        TenantId tenantId = accessGuard.requireActiveMembership(
                caller, AuthorityCatalog.OA_REQUESTS_READ);
        return new OaRequestAccess(tenantId, new MemberId(caller.actorId()),
                caller.hasAuthority(AuthorityCatalog.OA_REQUESTS_MANAGE));
    }

    /**
     * Resolves request-read access from a current locking membership read after the caller has
     * acquired the tenant serialization lock.
     */
    public OaRequestAccess accessForShare(AuthenticatedCaller caller) {
        TenantId tenantId = accessGuard.requireActiveMembershipForShare(
                caller, AuthorityCatalog.OA_REQUESTS_READ);
        return new OaRequestAccess(tenantId, new MemberId(caller.actorId()),
                caller.hasAuthority(AuthorityCatalog.OA_REQUESTS_MANAGE));
    }

    /** Gets the caller-bound OA tenant without reading membership state. */
    public TenantId tenant(AuthenticatedCaller caller) {
        return accessGuard.requireTenant(caller, AuthorityCatalog.OA_REQUESTS_READ);
    }

    public record OaRequestAccess(
            TenantId tenantId, MemberId memberId, boolean tenantManager) {
    }
}
