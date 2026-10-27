package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Requires both a verified authority and an active local membership for tenant-bound operations. */
@Component
@RequiredArgsConstructor
public class OrganizationAccessGuard {

    private final CallerGuard callerGuard;
    private final OrganizationMembershipRepository membershipRepository;

    public TenantId requireActiveMembership(AuthenticatedCaller caller, String authority) {
        TenantId tenantId = requireTenant(caller, authority);
        MemberId memberId = new MemberId(caller.actorId());
        if (membershipRepository.findByMember(tenantId, memberId)
                .filter(membership -> membership.isActive())
                .isEmpty()) {
            throw new NonRetryableApplicationException(
                    ApplicationErrorCode.ACCESS_DENIED,
                    "Caller is not an active member of this tenant");
        }
        return tenantId;
    }

    /**
     * Gets the caller-bound tenant without opening a membership snapshot. Callers holding the
     * tenant root lock must follow this with {@link #requireActiveMembershipForShare}.
     */
    public TenantId requireTenant(AuthenticatedCaller caller, String authority) {
        return callerGuard.requireTenant(caller, authority);
    }

    /**
     * Uses a locking current read after the tenant root has been serialized. This prevents an
     * earlier consistent read in a REPEATABLE_READ command transaction from admitting a member
     * that was concurrently deactivated before the tenant lock was acquired.
     */
    public TenantId requireActiveMembershipForShare(AuthenticatedCaller caller, String authority) {
        TenantId tenantId = requireTenant(caller, authority);
        MemberId memberId = new MemberId(caller.actorId());
        if (membershipRepository.findByMemberForShare(tenantId, memberId)
                .filter(membership -> membership.isActive())
                .isEmpty()) {
            throw new NonRetryableApplicationException(
                    ApplicationErrorCode.ACCESS_DENIED,
                    "Caller is not an active member of this tenant");
        }
        return tenantId;
    }
}
