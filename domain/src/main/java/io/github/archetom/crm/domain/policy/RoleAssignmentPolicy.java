package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.exception.AuthorizationDomainException;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.valueobject.AuthorityCode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cross-role rules for member assignment, delegation ceilings, and owner continuity. */
public final class RoleAssignmentPolicy {

    private RoleAssignmentPolicy() {
    }

    public static List<RoleGrant> effectiveGrants(TenantRole role) {
        if (role == null || !role.isActive()) {
            return List.of();
        }
        return configuredGrants(role);
    }

    public static List<RoleGrant> configuredGrants(TenantRole role) {
        if (role == null) {
            return List.of();
        }
        if (role.getSystemKey() == SystemRoleKey.TENANT_OWNER) {
            return AuthorityCatalog.tenantAssignable().stream()
                    .map(definition -> new RoleGrant(definition.code(),
                            definition.supportsDataScope() ? DataScope.TENANT : null))
                    .toList();
        }
        if (role.getSystemKey() == SystemRoleKey.TENANT_MEMBER) {
            return List.of(
                    RoleGrant.withoutScope(AuthorityCatalog.ORGANIZATION_READ),
                    RoleGrant.withoutScope(AuthorityCatalog.NAVIGATION_READ),
                    RoleGrant.withoutScope(AuthorityCatalog.NAVIGATION_WRITE));
        }
        return role.getGrants();
    }

    public static void requireAssignable(boolean activeMembership, TenantRole targetRole,
                                         Collection<RoleGrant> callerEffectiveGrants) {
        if (!activeMembership) {
            throw new AuthorizationDomainException("roles can only be assigned to active tenant members");
        }
        if (targetRole == null || !targetRole.isActive()) {
            throw new AuthorizationDomainException("only active roles can be assigned");
        }
        requireDelegable(targetRole, callerEffectiveGrants);
    }

    public static void requireDelegable(TenantRole targetRole, Collection<RoleGrant> callerEffectiveGrants) {
        if (targetRole == null) {
            throw new AuthorizationDomainException("target role must not be empty");
        }
        Map<AuthorityCode, RoleGrant> caller = new LinkedHashMap<>();
        for (RoleGrant grant : DataScopePolicy.mergeGrants(callerEffectiveGrants)) {
            caller.put(grant.authorityCode(), grant);
        }
        for (RoleGrant target : configuredGrants(targetRole)) {
            RoleGrant available = caller.get(target.authorityCode());
            if (available == null || target.dataScope() != null
                    && !DataScopePolicy.covers(available.dataScope(), target.dataScope())) {
                throw new AuthorizationDomainException("target role exceeds the caller's delegation ceiling");
            }
        }
    }

    public static boolean canDelegate(TenantRole targetRole, Collection<RoleGrant> callerEffectiveGrants) {
        try {
            requireDelegable(targetRole, callerEffectiveGrants);
            return true;
        } catch (AuthorizationDomainException exception) {
            return false;
        }
    }

    public static void requireRemovable(
            TenantRole role, boolean assignedMemberActive, long activeOwnerCount) {
        if (role == null) {
            throw new AuthorizationDomainException("role must not be empty");
        }
        if (role.getSystemKey() == SystemRoleKey.TENANT_MEMBER) {
            throw new AuthorizationDomainException("the tenant member role cannot be removed");
        }
        if (role.getSystemKey() == SystemRoleKey.TENANT_OWNER
                && assignedMemberActive && activeOwnerCount <= 1) {
            throw new AuthorizationDomainException("a tenant must retain at least one active owner");
        }
    }
}
