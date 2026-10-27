package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.PasswordLoginOptionsRequest;
import io.github.archetom.crm.api.dto.request.PasswordResetRequest;
import io.github.archetom.crm.api.dto.request.PasswordSessionCreateRequest;
import io.github.archetom.crm.application.port.out.authentication.AuthenticationTokenCodec;
import io.github.archetom.crm.application.port.out.authentication.PasswordLoginChallengeStore;
import io.github.archetom.crm.application.port.out.authentication.PasswordResetDelivery;
import io.github.archetom.crm.application.port.out.authentication.PasswordResetTokenStore;
import io.github.archetom.crm.application.security.BrowserSession;
import io.github.archetom.crm.application.security.PasswordLoginChallenge;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.application.vo.BrowserSessionVO;
import io.github.archetom.crm.application.vo.PasswordLoginOptionsVO;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.User;
import io.github.archetom.crm.domain.model.MembershipStatus;
import io.github.archetom.crm.domain.model.UserStatus;
import io.github.archetom.crm.domain.policy.PasswordPolicy;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.UserRepository;
import io.github.archetom.crm.domain.service.PasswordVerifier;
import io.github.archetom.crm.domain.service.UserDomainService;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.Email;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.MembershipId;
import io.github.archetom.crm.domain.valueobject.PasswordHash;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.UserId;
import io.github.archetom.crm.domain.valueobject.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

    private static final TenantId SOURCE_TENANT = new TenantId(11L);
    private static final TenantId TARGET_TENANT = new TenantId(22L);
    private static final TenantId UNRELATED_TENANT = new TenantId(33L);
    private static final UserId ACTOR = new UserId(101L);
    private static final long USER_VERSION = 4L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationMembershipRepository membershipRepository;
    @Mock
    private PasswordVerifier passwordVerifier;
    @Mock
    private UserDomainService userDomainService;
    @Mock
    private AuthenticationTokenCodec tokenCodec;
    @Mock
    private PasswordLoginChallengeStore passwordLoginChallengeStore;
    @Mock
    private PasswordResetTokenStore passwordResetTokenStore;
    @Mock
    private PasswordResetDelivery passwordResetDelivery;

    private AuthenticationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationServiceImpl(userRepository, membershipRepository, passwordVerifier,
                userDomainService, new PasswordPolicy(), tokenCodec, passwordLoginChallengeStore,
                passwordResetTokenStore, Optional.of(passwordResetDelivery),
                new CommandServiceTemplate("test-app", new TestTransactionManager()), new AfterCommitExecutor());
        ReflectionTestUtils.setField(service, "loginChallengeTtl", Duration.ofMinutes(5));
        ReflectionTestUtils.setField(service, "sessionTtl", Duration.ofHours(8));
        ReflectionTestUtils.setField(service, "passwordResetTtl", Duration.ofMinutes(15));
        ReflectionTestUtils.setField(service, "passwordResetUrl", "https://example.test/password-reset/confirm");
        ReflectionTestUtils.setField(service, "passwordResetFrom", "noreply@example.test");
    }

    @Test
    void verifiedPasswordReturnsOnlyServerResolvedActiveTenants() {
        User user = activeUser();
        when(userRepository.findByUsername(SOURCE_TENANT, "member_1")).thenReturn(Optional.of(user));
        when(passwordVerifier.matches("CorrectHorseBattery1", user.getPasswordHash())).thenReturn(true);
        when(membershipRepository.findActiveByMember(new MemberId(ACTOR.getValue())))
                .thenReturn(List.of(membership(SOURCE_TENANT, null), membership(TARGET_TENANT, 501L)));
        when(tokenCodec.issueLoginChallenge(any())).thenReturn("verified-challenge");

        Result<PasswordLoginOptionsVO> result = service.beginPasswordLogin(new PasswordLoginOptionsRequest()
                .setTenantId(SOURCE_TENANT.getValue())
                .setLoginName("member_1")
                .setPassword("CorrectHorseBattery1"));

        assertTrue(result.isSuccess());
        assertEquals("verified-challenge", result.getData().loginChallenge());
        assertEquals(List.of(SOURCE_TENANT.getValue(), TARGET_TENANT.getValue()), result.getData().tenants().stream()
                .map(tenant -> tenant.tenantId()).toList());
        assertEquals(501L, result.getData().tenants().get(1).departmentId());
        ArgumentCaptor<PasswordLoginChallenge> challenge = ArgumentCaptor.forClass(PasswordLoginChallenge.class);
        verify(tokenCodec).issueLoginChallenge(challenge.capture());
        assertEquals(ACTOR.getValue(), challenge.getValue().actorId());
        assertEquals(SOURCE_TENANT.getValue(), challenge.getValue().sourceTenantId());
        assertEquals(USER_VERSION, challenge.getValue().userVersion());
        verify(passwordLoginChallengeStore).issue(eq("verified-challenge"), any());
    }

    @Test
    void sessionCreationUsesTheVerifiedChallengeAndActiveSelectedMembership() {
        PasswordLoginChallenge challenge = challenge();
        when(tokenCodec.readLoginChallenge("verified-challenge")).thenReturn(Optional.of(challenge));
        when(userRepository.findById(SOURCE_TENANT, ACTOR)).thenReturn(Optional.of(activeUser()));
        when(membershipRepository.findByMember(TARGET_TENANT, new MemberId(ACTOR.getValue())))
                .thenReturn(Optional.of(membership(TARGET_TENANT, 501L)));
        when(passwordLoginChallengeStore.consume(eq("verified-challenge"), any())).thenReturn(true);
        when(tokenCodec.issueBrowserSession(any())).thenReturn("browser-session");

        Result<BrowserSessionVO> result = service.createBrowserSession(new PasswordSessionCreateRequest()
                .setLoginChallenge("verified-challenge")
                .setTenantId(TARGET_TENANT.getValue()));

        assertTrue(result.isSuccess());
        assertEquals(ACTOR.getValue(), result.getData().actorId());
        assertEquals(TARGET_TENANT.getValue(), result.getData().tenantId());
        assertEquals("browser-session", result.getData().token());
        ArgumentCaptor<BrowserSession> session = ArgumentCaptor.forClass(BrowserSession.class);
        verify(tokenCodec).issueBrowserSession(session.capture());
        assertEquals(ACTOR.getValue(), session.getValue().actorId());
        assertEquals(SOURCE_TENANT.getValue(), session.getValue().sourceTenantId());
        assertEquals(TARGET_TENANT.getValue(), session.getValue().tenantId());
        assertEquals(USER_VERSION, session.getValue().userVersion());
        verify(passwordLoginChallengeStore).consume(eq("verified-challenge"), any());
    }

    @Test
    void sessionCreationRejectsATenantOutsideTheVerifiedActiveMemberships() {
        when(tokenCodec.readLoginChallenge("verified-challenge")).thenReturn(Optional.of(challenge()));
        when(userRepository.findById(SOURCE_TENANT, ACTOR)).thenReturn(Optional.of(activeUser()));
        when(membershipRepository.findByMember(UNRELATED_TENANT, new MemberId(ACTOR.getValue())))
                .thenReturn(Optional.empty());

        Result<BrowserSessionVO> result = service.createBrowserSession(new PasswordSessionCreateRequest()
                .setLoginChallenge("verified-challenge")
                .setTenantId(UNRELATED_TENANT.getValue()));

        assertFalse(result.isSuccess());
        assertEquals("102", result.getErrorContext().fetchRootError().getErrorCode().getErrorSpecific());
        verify(tokenCodec, never()).issueBrowserSession(any());
        verify(passwordLoginChallengeStore, never()).consume(any(), any());
    }

    @Test
    void sessionCreationRejectsAnAlreadyConsumedLoginChallenge() {
        when(tokenCodec.readLoginChallenge("verified-challenge")).thenReturn(Optional.of(challenge()));
        when(userRepository.findById(SOURCE_TENANT, ACTOR)).thenReturn(Optional.of(activeUser()));
        when(membershipRepository.findByMember(TARGET_TENANT, new MemberId(ACTOR.getValue())))
                .thenReturn(Optional.of(membership(TARGET_TENANT, 501L)));
        when(passwordLoginChallengeStore.consume(eq("verified-challenge"), any())).thenReturn(false);

        Result<BrowserSessionVO> result = service.createBrowserSession(new PasswordSessionCreateRequest()
                .setLoginChallenge("verified-challenge")
                .setTenantId(TARGET_TENANT.getValue()));

        assertFalse(result.isSuccess());
        verify(tokenCodec, never()).issueBrowserSession(any());
    }

    @Test
    void sessionResumeFailsAfterTheSelectedTenantMembershipIsNoLongerActive() {
        BrowserSession session = new BrowserSession(ACTOR.getValue(), SOURCE_TENANT.getValue(),
                TARGET_TENANT.getValue(), USER_VERSION, Instant.now().plus(Duration.ofHours(1)));
        when(tokenCodec.readBrowserSession("browser-session")).thenReturn(Optional.of(session));
        when(userRepository.findById(SOURCE_TENANT, ACTOR)).thenReturn(Optional.of(activeUser()));
        when(membershipRepository.findByMember(TARGET_TENANT, new MemberId(ACTOR.getValue())))
                .thenReturn(Optional.empty());

        Optional<BrowserSessionVO> resumed = service.resumeBrowserSession("browser-session");

        assertTrue(resumed.isEmpty());
        verify(membershipRepository).findByMember(TARGET_TENANT, new MemberId(ACTOR.getValue()));
    }

    @Test
    void passwordResetIssuesOnlyAnOpaqueCapabilityAndDeliversItAfterTheCommand() {
        User user = activeUser();
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(15));
        when(userRepository.findByUsername(SOURCE_TENANT, "member_1")).thenReturn(Optional.of(user));
        when(passwordResetTokenStore.issue(eq(SOURCE_TENANT), eq(ACTOR), any()))
                .thenReturn(new PasswordResetTokenStore.IssuedPasswordResetToken(
                        "synthetic-reset-capability", expiresAt));

        Result<Void> result = service.requestPasswordReset(new PasswordResetRequest()
                .setTenantId(SOURCE_TENANT.getValue())
                .setLoginName("member_1"));

        assertTrue(result.isSuccess());
        ArgumentCaptor<PasswordResetDelivery.PasswordResetMessage> message = ArgumentCaptor.forClass(
                PasswordResetDelivery.PasswordResetMessage.class);
        verify(passwordResetDelivery).deliver(message.capture());
        assertEquals("member_1@example.test", message.getValue().recipientEmail());
        assertEquals(expiresAt, message.getValue().expiresAt());
    }

    private PasswordLoginChallenge challenge() {
        return new PasswordLoginChallenge(ACTOR.getValue(), SOURCE_TENANT.getValue(), USER_VERSION,
                Instant.now().plus(Duration.ofMinutes(2)));
    }

    private User activeUser() {
        LocalDateTime now = LocalDateTime.now();
        return User.reconstitute(new User.UserSnapshot(ACTOR, new Username("member_1"),
                new Email("member_1@example.test"), null, PasswordHash.fromTrustedHash("bcrypt-hash"),
                "Member", UserStatus.ACTIVE, SOURCE_TENANT, null, false, false, USER_VERSION, now, now));
    }

    private OrganizationMembership membership(TenantId tenantId, Long departmentId) {
        LocalDateTime now = LocalDateTime.now();
        return OrganizationMembership.reconstitute(new OrganizationMembership.Snapshot(
                new MembershipId(tenantId.getValue()), tenantId, new MemberId(ACTOR.getValue()),
                departmentId == null ? null : new DepartmentId(departmentId), MembershipStatus.ACTIVE,
                0L, now, now));
    }
}
