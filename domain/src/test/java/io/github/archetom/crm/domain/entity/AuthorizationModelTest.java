package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.AuthorizationDomainException;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.DataScopePolicy;
import io.github.archetom.crm.domain.policy.RoleAssignmentPolicy;
import io.github.archetom.crm.domain.valueobject.AuthorityCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorizationModelTest {

    private final TenantId tenantId = new TenantId(9L);

    @Test
    void authorityCatalogRejectsUnknownAndPlatformCapabilitiesFromRoleGrants() {
        assertThrows(AuthorizationDomainException.class,
                () -> RoleGrant.withoutScope("unknown:read"));
        assertThrows(AuthorizationDomainException.class,
                () -> RoleGrant.withoutScope(AuthorityCatalog.ORGANIZATION_BOOTSTRAP));
        assertThrows(AuthorizationDomainException.class,
                () -> RoleGrant.withoutScope(AuthorityCatalog.USERS_READ));
        assertThrows(AuthorizationDomainException.class,
                () -> RoleGrant.withScope(AuthorityCatalog.NAVIGATION_READ, DataScope.TENANT));
    }

    @Test
    void systemRolesAreImmutableAndOwnerCapabilitiesFollowTheCatalog() {
        TenantRole owner = TenantRole.createSystem(tenantId, SystemRoleKey.TENANT_OWNER);

        assertThrows(AuthorizationDomainException.class, () -> owner.update("Other", null));
        assertThrows(AuthorizationDomainException.class, () -> owner.deactivate());
        assertThrows(AuthorizationDomainException.class, () -> owner.replaceGrants(List.of()));
        assertEquals(AuthorityCatalog.tenantAssignable().size(),
                RoleAssignmentPolicy.effectiveGrants(owner).size());
    }

    @Test
    void grantsAreUniqueAndDataScopesMergeByExplicitCoverage() {
        assertThrows(AuthorizationDomainException.class, () -> TenantRole.createCustom(
                tenantId, "Support", null,
                List.of(RoleGrant.withScope(AuthorityCatalog.USERS_READ, DataScope.SELF),
                        RoleGrant.withScope(AuthorityCatalog.USERS_READ, DataScope.TENANT))));

        assertEquals(DataScope.DEPARTMENT_SUBTREE,
                DataScopePolicy.merge(List.of(DataScope.SELF, DataScope.DEPARTMENT_SUBTREE)).orElseThrow());
        assertEquals(DataScope.SELF,
                DataScopePolicy.restrictToMembership(DataScope.DEPARTMENT, null));
    }

    @Test
    void delegationCannotExceedTheCallersEffectiveScope() {
        TenantRole target = TenantRole.createCustom(tenantId, "Tenant Readers", null,
                List.of(RoleGrant.withScope(AuthorityCatalog.USERS_READ, DataScope.TENANT)));

        assertThrows(AuthorizationDomainException.class, () -> RoleAssignmentPolicy.requireAssignable(
                true, target,
                List.of(RoleGrant.withScope(AuthorityCatalog.USERS_READ, DataScope.DEPARTMENT_SUBTREE))));

        RoleAssignmentPolicy.requireAssignable(true, target,
                List.of(RoleGrant.withScope(AuthorityCatalog.USERS_READ, DataScope.TENANT)));
    }

    @Test
    void memberAssignmentAndLastOwnerRulesRemainExplicit() {
        TenantRole member = TenantRole.createSystem(tenantId, SystemRoleKey.TENANT_MEMBER);
        TenantRole owner = TenantRole.createSystem(tenantId, SystemRoleKey.TENANT_OWNER);

        assertThrows(AuthorizationDomainException.class,
                () -> RoleAssignmentPolicy.requireRemovable(member, true, 2));
        assertThrows(AuthorizationDomainException.class,
                () -> RoleAssignmentPolicy.requireRemovable(owner, true, 1));
        RoleAssignmentPolicy.requireRemovable(owner, true, 2);
        RoleAssignmentPolicy.requireRemovable(owner, false, 1);
    }

    @Test
    void navigationPreferenceOnlyHidesOrReordersKnownVisibleCodes() {
        NavigationPreference preference = NavigationPreference.create(
                tenantId, new MemberId(7L),
                List.of("authorization.roles", "home", "home"),
                List.of("users"),
                Set.of("home", "users", "authorization.roles"));

        assertEquals(List.of("authorization.roles", "home"),
                preference.applyTo(List.of("home", "users", "authorization.roles")));
        assertThrows(AuthorizationDomainException.class, () -> preference.change(
                List.of("unknown"), List.of(), Set.of("home")));
    }

    @Test
    void authorityCodesCannotBeUrlsOrNumericMenuIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> new AuthorityCode("/api/v1/users"));
        assertThrows(IllegalArgumentException.class, () -> new AuthorityCode("1001"));
    }
}
