package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.RoleCreateRequest;
import io.github.archetom.crm.api.dto.request.RoleGrantRequest;
import io.github.archetom.crm.api.dto.request.RoleQueryRequest;
import io.github.archetom.crm.application.security.CallerGuard;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.security.TenantAuthorizationChangeNotifier;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.MembershipStatus;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.policy.RoleAssignmentPolicy;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.RoleAssignmentRepository;
import io.github.archetom.crm.domain.repository.TenantRoleRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.MembershipId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.common.result.Result;
import io.github.archetom.common.result.Pager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.github.archetom.crm.domain.policy.AuthorityCatalog.AUTHORIZATION_ASSIGN;
import static io.github.archetom.crm.domain.policy.AuthorityCatalog.AUTHORIZATION_READ;
import static io.github.archetom.crm.domain.policy.AuthorityCatalog.AUTHORIZATION_WRITE;
import static io.github.archetom.crm.domain.policy.AuthorityCatalog.NAVIGATION_READ;
import static io.github.archetom.crm.domain.policy.AuthorityCatalog.ORGANIZATION_REQUEST;
import static io.github.archetom.crm.domain.policy.AuthorityCatalog.USERS_READ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceImplTest {

    private static final TenantId TENANT = new TenantId(99L);
    private static final MemberId CALLER = new MemberId(7L);

    @Mock
    private TenantRoleRepository roleRepository;
    @Mock
    private RoleAssignmentRepository assignmentRepository;
    @Mock
    private OrganizationMembershipRepository membershipRepository;
    @Mock
    private OrganizationTenantRepository organizationTenantRepository;
    @Mock
    private TenantAuthorizationEvaluator authorizationEvaluator;
    @Mock
    private TenantAuthorizationChangeNotifier authorizationChangeNotifier;
    @Mock
    private PlatformTransactionManager transactionManager;

    private AuthorizationServiceImpl service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        CallerGuard callerGuard = new CallerGuard();
        service = new AuthorizationServiceImpl(
                roleRepository, assignmentRepository, membershipRepository,
                organizationTenantRepository, authorizationEvaluator, authorizationChangeNotifier,
                new OrganizationAccessGuard(callerGuard, membershipRepository),
                new CommandServiceTemplate("test-app", transactionManager),
                new QueryServiceTemplate("test-app", new TestTransactionManager()));
    }

    @Test
    void roleWritesRequireBothWriteAndAssignmentAuthoritiesBeforeTenantMutation() {
        Result<?> result = service.createRole(caller(AUTHORIZATION_WRITE),
                new RoleCreateRequest().setName("Support"));

        assertFailure(result, "103");
        verifyNoInteractions(roleRepository, assignmentRepository, organizationTenantRepository,
                authorizationEvaluator);
    }

    @Test
    void activeMembershipIsRequiredBeforeAnyRoleRepositoryAccess() {
        when(membershipRepository.findByMember(TENANT, CALLER)).thenReturn(Optional.empty());

        Result<?> result = service.createRole(caller(AUTHORIZATION_WRITE, AUTHORIZATION_ASSIGN),
                new RoleCreateRequest().setName("Support"));

        assertFailure(result, "103");
        verifyNoInteractions(roleRepository, organizationTenantRepository, authorizationEvaluator);
    }

    @Test
    void roleCreationRejectsADataScopeAboveTheCallersCurrentDatabaseCeiling() {
        activeMembership(CALLER);
        when(organizationTenantRepository.lockForTenantChanges(TENANT)).thenReturn(true);
        when(authorizationEvaluator.evaluate(TENANT, CALLER)).thenReturn(new TenantAuthorizationEvaluator.Evaluation(
                membership(CALLER), List.of(),
                List.of(RoleGrant.withScope(USERS_READ, DataScope.DEPARTMENT_SUBTREE))));
        RoleCreateRequest request = new RoleCreateRequest()
                .setName("Tenant Readers")
                .setGrants(List.of(new RoleGrantRequest()
                        .setAuthorityCode(USERS_READ)
                        .setDataScope("TENANT")));

        Result<?> result = service.createRole(caller(AUTHORIZATION_WRITE, AUTHORIZATION_ASSIGN), request);

        assertFailure(result, "303");
        verify(roleRepository, never()).save(any(), any());
    }

    @Test
    void roleQueryUsesRepositoryPagination() {
        activeMembership(CALLER);
        TenantRole role = TenantRole.createCustom(TENANT, "Support", null, List.of());
        role.onPersisted(new RoleId(10L), 0L, LocalDateTime.now(), LocalDateTime.now());
        when(roleRepository.findPage(TENANT, null, null, 2, 10))
                .thenReturn(new PageResult<>(2, 10, 11, List.of(role)));
        RoleQueryRequest request = new RoleQueryRequest();
        request.setPage(2);
        request.setSize(10);

        Result<Pager<io.github.archetom.crm.application.vo.TenantRoleVO>> result = service.queryRoles(
                caller(AUTHORIZATION_READ), request);

        assertTrue(result.isSuccess());
        assertEquals(11L, result.getData().getTotalNum());
        assertEquals(List.of(10L), result.getData().getObjectList().stream()
                .map(io.github.archetom.crm.application.vo.TenantRoleVO::id).toList());
        verify(roleRepository).findPage(TENANT, null, null, 2, 10);
    }

    @Test
    void assignableRoleQueryFiltersBoundedRepositoryPagesWithoutLoadingAllRolesAtOnce() {
        activeMembership(CALLER);
        TenantRole firstAssignable = persistedRole(10L, "First", List.of());
        TenantRole aboveCallerCeiling = persistedRole(11L, "Tenant Readers",
                List.of(RoleGrant.withScope(USERS_READ, DataScope.TENANT)));
        TenantRole secondAssignable = persistedRole(12L, "Second", List.of());
        when(authorizationEvaluator.evaluate(TENANT, CALLER))
                .thenReturn(new TenantAuthorizationEvaluator.Evaluation(
                        membership(CALLER), List.of(), List.of()));
        when(roleRepository.findPage(TENANT, null, null, 1, 200))
                .thenReturn(new PageResult<>(1, 200, 201,
                        List.of(firstAssignable, aboveCallerCeiling)));
        when(roleRepository.findPage(TENANT, null, null, 2, 200))
                .thenReturn(new PageResult<>(2, 200, 201, List.of(secondAssignable)));
        RoleQueryRequest request = new RoleQueryRequest().setAssignableOnly(true);
        request.setPage(2);
        request.setSize(1);

        Result<Pager<io.github.archetom.crm.application.vo.TenantRoleVO>> result = service.queryRoles(
                caller(AUTHORIZATION_READ, AUTHORIZATION_ASSIGN), request);

        assertTrue(result.isSuccess());
        assertEquals(2L, result.getData().getTotalNum());
        assertEquals(List.of(12L), result.getData().getObjectList().stream()
                .map(io.github.archetom.crm.application.vo.TenantRoleVO::id).toList());
        verify(roleRepository).findPage(TENANT, null, null, 1, 200);
        verify(roleRepository).findPage(TENANT, null, null, 2, 200);
    }

    @Test
    void currentAuthorizationIncludesVerifiedCallerAuthoritiesForNavigationGuards() {
        activeMembership(CALLER);
        when(authorizationEvaluator.evaluate(TENANT, CALLER)).thenReturn(
                new TenantAuthorizationEvaluator.Evaluation(
                        membership(CALLER), List.of(),
                        List.of(RoleGrant.withScope(USERS_READ, DataScope.SELF))));

        Result<io.github.archetom.crm.application.vo.MemberAuthorizationVO> result =
                service.getMyAuthorization(caller(NAVIGATION_READ, ORGANIZATION_REQUEST));

        assertTrue(result.isSuccess());
        assertEquals(Set.of(NAVIGATION_READ, ORGANIZATION_REQUEST, USERS_READ),
                result.getData().authorities());
        assertEquals(DataScope.SELF.name(), result.getData().dataScopes().get(USERS_READ));
    }

    @Test
    void memberAuthorizationLookupNeverCopiesTheRequestersPlatformAuthorities() {
        MemberId target = new MemberId(8L);
        activeMembership(CALLER);
        when(membershipRepository.findByMember(TENANT, target)).thenReturn(Optional.of(membership(target)));
        when(authorizationEvaluator.evaluate(TENANT, target)).thenReturn(
                new TenantAuthorizationEvaluator.Evaluation(
                        membership(target), List.of(),
                        List.of(RoleGrant.withScope(USERS_READ, DataScope.SELF))));

        Result<io.github.archetom.crm.application.vo.MemberAuthorizationVO> result =
                service.getMemberRoles(caller(AUTHORIZATION_READ, ORGANIZATION_REQUEST), target.getValue());

        assertTrue(result.isSuccess());
        assertEquals(Set.of(USERS_READ), result.getData().authorities());
    }

    @Test
    void removingTheLastActiveOwnerFailsInsideTheTenantLock() {
        MemberId target = new MemberId(8L);
        activeMembership(CALLER);
        when(membershipRepository.findByMember(TENANT, target)).thenReturn(Optional.of(membership(target)));
        when(organizationTenantRepository.lockForTenantChanges(TENANT)).thenReturn(true);
        TenantRole owner = TenantRole.createSystem(TENANT, SystemRoleKey.TENANT_OWNER);
        owner.onPersisted(new RoleId(10L), 0L, LocalDateTime.now(), LocalDateTime.now());
        when(roleRepository.findById(TENANT, new RoleId(10L))).thenReturn(Optional.of(owner));
        when(authorizationEvaluator.evaluate(TENANT, CALLER)).thenReturn(new TenantAuthorizationEvaluator.Evaluation(
                membership(CALLER), List.of(owner), RoleAssignmentPolicy.effectiveGrants(owner)));
        when(assignmentRepository.exists(TENANT, target, new RoleId(10L))).thenReturn(true);
        when(assignmentRepository.countActiveMembersByRole(TENANT, new RoleId(10L))).thenReturn(1L);

        Result<?> result = service.removeRoleAssignment(
                caller(AUTHORIZATION_ASSIGN), 10L, target.getValue());

        assertFailure(result, "303");
        verify(assignmentRepository, never()).delete(any(), any(), any());
    }

    @Test
    void removingAnOwnerAssignmentFromASuspendedMemberKeepsTheActiveOwnerInvariant() {
        MemberId target = new MemberId(8L);
        activeMembership(CALLER);
        when(membershipRepository.findByMember(TENANT, target))
                .thenReturn(Optional.of(membership(target, MembershipStatus.SUSPENDED)));
        when(organizationTenantRepository.lockForTenantChanges(TENANT)).thenReturn(true);
        TenantRole owner = TenantRole.createSystem(TENANT, SystemRoleKey.TENANT_OWNER);
        RoleId ownerId = new RoleId(10L);
        owner.onPersisted(ownerId, 0L, LocalDateTime.now(), LocalDateTime.now());
        when(roleRepository.findById(TENANT, ownerId)).thenReturn(Optional.of(owner));
        when(authorizationEvaluator.evaluate(TENANT, CALLER)).thenReturn(new TenantAuthorizationEvaluator.Evaluation(
                membership(CALLER), List.of(owner), RoleAssignmentPolicy.effectiveGrants(owner)));
        when(assignmentRepository.exists(TENANT, target, ownerId)).thenReturn(true);
        when(assignmentRepository.delete(TENANT, target, ownerId)).thenReturn(true);

        Result<?> result = service.removeRoleAssignment(
                caller(AUTHORIZATION_ASSIGN), ownerId.getValue(), target.getValue());

        assertTrue(result.isSuccess());
        verify(assignmentRepository, never()).countActiveMembersByRole(any(), any());
        verify(assignmentRepository).delete(TENANT, target, ownerId);
    }

    private void activeMembership(MemberId memberId) {
        when(membershipRepository.findByMember(TENANT, memberId)).thenReturn(Optional.of(membership(memberId)));
    }

    private OrganizationMembership membership(MemberId memberId) {
        return membership(memberId, MembershipStatus.ACTIVE);
    }

    private OrganizationMembership membership(MemberId memberId, MembershipStatus status) {
        return OrganizationMembership.reconstitute(new OrganizationMembership.Snapshot(
                new MembershipId(memberId.getValue()), TENANT, memberId, null,
                status, 0L, LocalDateTime.now(), LocalDateTime.now()));
    }

    private TenantRole persistedRole(long id, String name, List<RoleGrant> grants) {
        TenantRole role = TenantRole.createCustom(TENANT, name, null, grants);
        role.onPersisted(new RoleId(id), 0L, LocalDateTime.now(), LocalDateTime.now());
        return role;
    }

    private AuthenticatedCaller caller(String... authorities) {
        return new AuthenticatedCaller(CALLER.getValue(), TENANT.getValue(), Set.of(authorities));
    }

    private void assertFailure(Result<?> result, String specificCode) {
        assertFalse(result.isSuccess());
        assertEquals(specificCode, result.getErrorContext().fetchRootError().getErrorCode().getErrorSpecific());
    }
}
