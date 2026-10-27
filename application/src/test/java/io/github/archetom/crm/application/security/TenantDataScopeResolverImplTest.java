package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.github.archetom.crm.domain.policy.AuthorityCatalog.ORGANIZATION_READ;
import static io.github.archetom.crm.domain.policy.AuthorityCatalog.USERS_READ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantDataScopeResolverImplTest {

    @Test
    void resolvesOnlyScopedAuthorityAlreadyPresentOnVerifiedCaller() {
        TenantAuthorizationEvaluator evaluator = mock(TenantAuthorizationEvaluator.class);
        OrganizationMembership membership = mock(OrganizationMembership.class);
        when(membership.isActive()).thenReturn(true);
        when(evaluator.evaluate(new TenantId(9L), new MemberId(7L))).thenReturn(
                new TenantAuthorizationEvaluator.Evaluation(membership, List.of(),
                        List.of(RoleGrant.withScope(USERS_READ, DataScope.DEPARTMENT))));
        TenantDataScopeResolver resolver = new TenantDataScopeResolverImpl(evaluator);
        AuthenticatedCaller caller = new AuthenticatedCaller(7L, 9L, Set.of(USERS_READ, ORGANIZATION_READ));

        assertEquals(DataScope.DEPARTMENT, resolver.resolve(caller, USERS_READ).orElseThrow());
        assertTrue(resolver.resolve(caller, ORGANIZATION_READ).isEmpty());
        assertTrue(resolver.resolve(new AuthenticatedCaller(7L, 9L, Set.of()), USERS_READ).isEmpty());
    }
}
