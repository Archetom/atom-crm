package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.api.dto.request.UserCreateRequest;
import io.github.archetom.crm.api.dto.request.UserQueryRequest;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.application.security.CallerGuard;
import io.github.archetom.crm.application.security.TenantAuthorizationChangeNotifier;
import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.User;
import io.github.archetom.crm.domain.event.DomainEventPublisher;
import io.github.archetom.crm.domain.factory.UserFactory;
import io.github.archetom.crm.domain.model.UserStatus;
import io.github.archetom.crm.domain.repository.UserRepository;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.service.UserDomainService;
import io.github.archetom.crm.domain.valueobject.Email;
import io.github.archetom.crm.domain.valueobject.PasswordHash;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.UserId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.Username;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserDomainService userDomainService;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private UserCacheService userCacheService;
    @Mock
    private UserFactory userFactory;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private MemberProfileRepository memberProfileRepository;
    @Mock
    private OrganizationMembershipRepository membershipRepository;
    @Mock
    private SystemRoleProvisioner systemRoleProvisioner;
    @Mock
    private TenantAuthorizationChangeNotifier authorizationChangeNotifier;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(
                        transactionManager.getTransaction(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SimpleTransactionStatus());
        service = new UserServiceImpl(
                userRepository,
                userDomainService,
                domainEventPublisher,
                new CommandServiceTemplate("test-app", transactionManager),
                new QueryServiceTemplate("test-app", new TestTransactionManager()),
                userCacheService,
                userFactory,
                afterCommitExecutor,
                new CallerGuard(),
                memberProfileRepository,
                membershipRepository,
                systemRoleProvisioner,
                authorizationChangeNotifier);
    }

    @Test
    void statusUpdateCannotBypassDeleteAuthority() {
        Result<Void> result = service.updateUserStatus(caller("users:write"), 10L, "DELETED");

        assertFailure(result, "002");
        verifyNoInteractions(userRepository);
    }

    @Test
    void deleteRequiresDeleteAuthority() {
        Result<Void> result = service.deleteUser(caller("users:write"), 10L);

        assertFailure(result, "103");
        verifyNoInteractions(userRepository);
    }

    @Test
    void rejectsUnboundedPageSizeBeforeRepositoryAccess() {
        UserQueryRequest request = new UserQueryRequest();
        request.setSize(QueryRequest.MAX_SIZE + 1);

        Result<?> result = service.queryUsers(caller("users:read"), request);

        assertFailure(result, "101");
        verifyNoInteractions(userRepository);
    }

    @Test
    void invalidDomainValueBecomesParameterError() {
        UserCreateRequest request = new UserCreateRequest()
                .setUsername("alice")
                .setEmail("alice@localhost")
                .setPassword("long-enough-password");
        when(userFactory.createStandardUser(
                new TenantId(99L), "alice", "alice@localhost",
                "long-enough-password", null))
                .thenThrow(new IllegalArgumentException("invalid email"));

        Result<?> result = service.createUser(caller("users:write"), request);

        assertFailure(result, "101");
        verifyNoInteractions(userRepository);
    }

    @Test
    void deletedUserIsNotVisibleOrMutable() {
        TenantId tenantId = new TenantId(99L);
        UserId userId = new UserId(10L);
        when(userRepository.findById(tenantId, userId)).thenReturn(Optional.of(deletedUser(tenantId, userId)));

        Result<?> getResult = service.getUserById(caller("users:read"), 10L);
        Result<?> updateResult = service.updateUserStatus(caller("users:write"), 10L, "ACTIVE");
        Result<?> deleteResult = service.deleteUser(caller("users:delete"), 10L);

        assertFailure(getResult, "300");
        assertFailure(updateResult, "300");
        assertFailure(deleteResult, "300");
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void administratorCannotBeDeleted() {
        TenantId tenantId = new TenantId(99L);
        UserId userId = new UserId(10L);
        when(userRepository.findById(tenantId, userId)).thenReturn(Optional.of(adminUser(tenantId, userId)));
        when(userDomainService.canDeleteUser(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        Result<?> result = service.deleteUser(caller("users:delete"), 10L);

        assertFailure(result, "002");
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void eventFailureCannotSkipIndependentCacheInvalidation() {
        TenantId tenantId = new TenantId(99L);
        UserId userId = new UserId(10L);
        User user = activeUser(tenantId, userId);
        when(userRepository.findById(tenantId, userId)).thenReturn(Optional.of(user));
        when(userRepository.save(tenantId, user)).thenReturn(user);

        Result<Void> result = service.updateUserStatus(caller("users:write"), 10L, "LOCKED");

        assertTrue(result.isSuccess());
        var callbacks = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommitExecutor, org.mockito.Mockito.times(2)).execute(org.mockito.ArgumentMatchers.anyString(), callbacks.capture());
        List<Runnable> actions = callbacks.getAllValues();
        doThrow(new IllegalStateException("publisher unavailable"))
                .when(domainEventPublisher).publishAll(anyList());
        assertThrows(IllegalStateException.class, actions.get(0)::run);
        actions.get(1).run();
        verify(userCacheService).invalidateUser(tenantId, userId);
    }

    @Test
    void creatingUserAlsoCreatesAnActiveMemberAndBaselineRole() {
        TenantId tenantId = new TenantId(99L);
        User user = User.createWithPasswordHash(tenantId, new Username("new_user"),
                new Email("new@example.com"), PasswordHash.fromTrustedHash("password-hash"), "New User");
        user.onPersisted(new UserId(12L), 0L, LocalDateTime.now(), LocalDateTime.now());
        when(userFactory.createStandardUser(tenantId, "new_user", "new@example.com",
                "long-enough-password", "New User")).thenReturn(user);
        when(userRepository.save(tenantId, user)).thenReturn(user);

        Result<?> result = service.createUser(caller("users:write"), new UserCreateRequest()
                .setUsername("new_user")
                .setEmail("new@example.com")
                .setPassword("long-enough-password")
                .setRealName("New User"));

        assertTrue(result.isSuccess());
        var profile = org.mockito.ArgumentCaptor.forClass(MemberProfile.class);
        verify(memberProfileRepository).save(profile.capture());
        assertEquals(new MemberId(12L), profile.getValue().getId());
        assertTrue(profile.getValue().isActive());
        verify(membershipRepository).save(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.argThat(
                membership -> membership.getMemberId().sameValueAs(new MemberId(12L))
                        && membership.isActive()));
        verify(systemRoleProvisioner).provisionMember(tenantId, new MemberId(12L));
        verify(authorizationChangeNotifier).changed(tenantId);
    }

    @Test
    void lockingUserSuspendsItsExistingOrganizationMember() {
        TenantId tenantId = new TenantId(99L);
        UserId userId = new UserId(10L);
        User user = activeUser(tenantId, userId);
        MemberId memberId = new MemberId(10L);
        OrganizationMembership membership = OrganizationMembership.activate(tenantId, memberId, null);
        MemberProfile profile = MemberProfile.register(memberId, "Active User", new Email("active@example.com"), null);
        when(userRepository.findById(tenantId, userId)).thenReturn(Optional.of(user));
        when(userRepository.save(tenantId, user)).thenReturn(user);
        when(membershipRepository.findByMember(tenantId, memberId)).thenReturn(Optional.of(membership));
        when(memberProfileRepository.findById(memberId)).thenReturn(Optional.of(profile));

        Result<Void> result = service.updateUserStatus(caller("users:write"), 10L, "LOCKED");

        assertTrue(result.isSuccess());
        assertFalse(membership.isActive());
        assertFalse(profile.isActive());
        verify(membershipRepository).save(tenantId, membership);
        verify(memberProfileRepository).save(profile);
        verify(authorizationChangeNotifier).changed(tenantId);
    }

    @Test
    void creatingUserFailsClosedWhenItsGeneratedIdAlreadyBelongsToAVerifiedMember() {
        TenantId tenantId = new TenantId(99L);
        User user = User.createWithPasswordHash(tenantId, new Username("new_user"),
                new Email("new@example.com"), PasswordHash.fromTrustedHash("password-hash"), "New User");
        user.onPersisted(new UserId(12L), 0L, LocalDateTime.now(), LocalDateTime.now());
        MemberId memberId = new MemberId(12L);
        when(userFactory.createStandardUser(tenantId, "new_user", "new@example.com",
                "long-enough-password", "New User")).thenReturn(user);
        when(userRepository.save(tenantId, user)).thenReturn(user);
        when(memberProfileRepository.findById(memberId)).thenReturn(Optional.of(
                MemberProfile.register(memberId, "Existing Member", new Email("existing@example.com"), null)));

        Result<?> result = service.createUser(caller("users:write"), new UserCreateRequest()
                .setUsername("new_user")
                .setEmail("new@example.com")
                .setPassword("long-enough-password")
                .setRealName("New User"));

        assertFailure(result, "302");
        verify(membershipRepository, never()).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(systemRoleProvisioner, never()).provisionMember(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private AuthenticatedCaller caller(String authority) {
        return new AuthenticatedCaller(7L, 99L, Set.of(authority));
    }

    private User deletedUser(TenantId tenantId, UserId userId) {
        return User.reconstitute(new User.UserSnapshot(
                userId,
                new Username("deleted_user"),
                new Email("deleted@example.com"),
                null,
                PasswordHash.fromTrustedHash("password-hash"),
                "Deleted User",
                UserStatus.DELETED,
                tenantId,
                null,
                false,
                false,
                1L,
                LocalDateTime.now(),
                LocalDateTime.now()));
    }

    private User adminUser(TenantId tenantId, UserId userId) {
        return User.reconstitute(new User.UserSnapshot(
                userId,
                new Username("admin_user"),
                new Email("admin@example.com"),
                null,
                PasswordHash.fromTrustedHash("password-hash"),
                "Admin User",
                UserStatus.ACTIVE,
                tenantId,
                null,
                false,
                true,
                1L,
                LocalDateTime.now(),
                LocalDateTime.now()));
    }

    private User activeUser(TenantId tenantId, UserId userId) {
        return User.reconstitute(new User.UserSnapshot(
                userId,
                new Username("active_user"),
                new Email("active@example.com"),
                null,
                PasswordHash.fromTrustedHash("password-hash"),
                "Active User",
                UserStatus.ACTIVE,
                tenantId,
                null,
                false,
                false,
                1L,
                LocalDateTime.now(),
                LocalDateTime.now()));
    }

    private void assertFailure(Result<?> result, String expectedSpecificCode) {
        assertFalse(result.isSuccess());
        assertEquals(expectedSpecificCode,
                result.getErrorContext().fetchRootError().getErrorCode().getErrorSpecific());
    }
}
