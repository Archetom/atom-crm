package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.InitialTenantAdminRequest;
import io.github.archetom.crm.application.port.out.authentication.AuthenticationTokenCodec;
import io.github.archetom.crm.application.port.out.installation.SystemInstallationStore;
import io.github.archetom.crm.application.security.TenantAuthorizationChangeNotifier;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.application.vo.BrowserSessionVO;
import io.github.archetom.crm.application.vo.InitialInstallationStatusVO;
import io.github.archetom.crm.domain.entity.User;
import io.github.archetom.crm.domain.event.DomainEventPublisher;
import io.github.archetom.crm.domain.factory.UserFactory;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.UserRepository;
import io.github.archetom.crm.domain.valueobject.Email;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for the public, single-winner first-installation command boundary. */
@ExtendWith(MockitoExtension.class)
class SystemInstallationServiceImplTest {

    private static final TenantId INITIAL_TENANT = new TenantId(1L);

    @Mock private SystemInstallationStore installationStore;
    @Mock private UserFactory userFactory;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationTenantRepository organizationTenantRepository;
    @Mock private MemberProfileRepository memberProfileRepository;
    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private SystemRoleProvisioner systemRoleProvisioner;
    @Mock private CustomerFieldProvisioner customerFieldProvisioner;
    @Mock private LeadFieldProvisioner leadFieldProvisioner;
    @Mock private ContactFieldProvisioner contactFieldProvisioner;
    @Mock private OpportunityFieldProvisioner opportunityFieldProvisioner;
    @Mock private OpportunityPipelineProvisioner opportunityPipelineProvisioner;
    @Mock private ProductFieldProvisioner productFieldProvisioner;
    @Mock private ContractFieldProvisioner contractFieldProvisioner;
    @Mock private InvoiceFieldProvisioner invoiceFieldProvisioner;
    @Mock private ReceivableFieldProvisioner receivableFieldProvisioner;
    @Mock private ReceivablePlanFieldProvisioner receivablePlanFieldProvisioner;
    @Mock private ReturnVisitFieldProvisioner returnVisitFieldProvisioner;
    @Mock private OaRequestFieldProvisioner oaRequestFieldProvisioner;
    @Mock private TenantAuthorizationChangeNotifier authorizationChangeNotifier;
    @Mock private AuthenticationTokenCodec tokenCodec;
    @Mock private DomainEventPublisher domainEventPublisher;
    @Mock private UserCacheService userCacheService;
    @Mock private AfterCommitExecutor afterCommitExecutor;

    private SystemInstallationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SystemInstallationServiceImpl(
                installationStore, userFactory, userRepository, organizationTenantRepository,
                memberProfileRepository, membershipRepository, systemRoleProvisioner,
                customerFieldProvisioner, leadFieldProvisioner, contactFieldProvisioner,
                opportunityFieldProvisioner, opportunityPipelineProvisioner, productFieldProvisioner,
                contractFieldProvisioner, invoiceFieldProvisioner, receivableFieldProvisioner,
                receivablePlanFieldProvisioner, returnVisitFieldProvisioner, oaRequestFieldProvisioner,
                authorizationChangeNotifier, tokenCodec, domainEventPublisher, userCacheService,
                new CommandServiceTemplate("test-app", new TestTransactionManager()),
                new QueryServiceTemplate("test-app", new TestTransactionManager()), afterCommitExecutor);
        ReflectionTestUtils.setField(service, "sessionTtl", Duration.ofHours(8));
    }

    @Test
    void exposesOnlyWhetherTheFirstInstallationIsStillRequired() {
        when(installationStore.installationRequired()).thenReturn(true);

        Result<InitialInstallationStatusVO> result = service.installationStatus();

        assertTrue(result.isSuccess());
        assertTrue(result.getData().installationRequired());
    }

    @Test
    void createsTheInitialOwnerRolesDefaultsAndBrowserSessionInOneCommand() {
        User administrator = User.createInitialAdministratorWithPasswordHash(INITIAL_TENANT, new Username("workspace_admin"),
                new Email("admin@example.test"), PasswordHash.fromTrustedHash("stored-hash"), "系统管理员");
        when(userFactory.createInitialAdministrator(INITIAL_TENANT, "workspace_admin", "admin@example.test",
                "CorrectHorse1", "系统管理员")).thenReturn(administrator);
        when(installationStore.installationRequired()).thenReturn(true);
        when(installationStore.claimInitialInstallation()).thenReturn(true);
        when(userRepository.save(eq(INITIAL_TENANT), any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(1);
            saved.onPersisted(new UserId(88L), 0L, null, null);
            return saved;
        });
        when(organizationTenantRepository.initialize(eq(INITIAL_TENANT), any())).thenReturn(true);
        when(tokenCodec.issueBrowserSession(any())).thenReturn("protected-session-token");

        Result<BrowserSessionVO> result = service.initializeTenantAdmin(new InitialTenantAdminRequest()
                .setDisplayName("系统管理员")
                .setLoginName("workspace_admin")
                .setEmail("admin@example.test")
                .setPassword("CorrectHorse1"));

        assertTrue(result.isSuccess());
        assertEquals(88L, result.getData().actorId());
        assertEquals(1L, result.getData().tenantId());
        assertEquals("protected-session-token", result.getData().token());
        assertTrue(result.getData().expiresAt().isAfter(Instant.now()));
        assertTrue(administrator.isAdmin());
        verify(systemRoleProvisioner).provisionOwner(INITIAL_TENANT,
                new io.github.archetom.crm.domain.valueobject.MemberId(88L));
        verify(customerFieldProvisioner).provision(INITIAL_TENANT);
        verify(opportunityPipelineProvisioner).provision(INITIAL_TENANT);
        verify(oaRequestFieldProvisioner).provision(INITIAL_TENANT);
        verify(authorizationChangeNotifier).changed(INITIAL_TENANT);
        ArgumentCaptor<io.github.archetom.crm.application.security.BrowserSession> session =
                ArgumentCaptor.forClass(io.github.archetom.crm.application.security.BrowserSession.class);
        verify(tokenCodec).issueBrowserSession(session.capture());
        assertEquals(88L, session.getValue().actorId());
        assertEquals(1L, session.getValue().tenantId());
    }

    @Test
    void refusesASecondInitializationBeforeCreatingAnotherAccount() {
        User administrator = User.createInitialAdministratorWithPasswordHash(INITIAL_TENANT, new Username("workspace_admin"),
                new Email("admin@example.test"), PasswordHash.fromTrustedHash("stored-hash"), "系统管理员");
        when(userFactory.createInitialAdministrator(any(), any(), any(), any(), any())).thenReturn(administrator);
        when(installationStore.installationRequired()).thenReturn(true);
        when(installationStore.claimInitialInstallation()).thenReturn(false);

        Result<BrowserSessionVO> result = service.initializeTenantAdmin(new InitialTenantAdminRequest()
                .setDisplayName("系统管理员")
                .setLoginName("workspace_admin")
                .setEmail("admin@example.test")
                .setPassword("CorrectHorse1"));

        assertFalse(result.isSuccess());
        assertEquals("302", result.getErrorContext().fetchRootError().getErrorCode().getErrorSpecific());
        verify(userRepository, never()).save(any(), any());
        verify(organizationTenantRepository, never()).initialize(any(), any());
    }

    @Test
    void refusesAnAlreadyInstalledSystemBeforeAttemptingTheAtomicClaim() {
        when(installationStore.installationRequired()).thenReturn(false);

        Result<BrowserSessionVO> result = service.initializeTenantAdmin(new InitialTenantAdminRequest()
                .setDisplayName("系统管理员")
                .setLoginName("workspace_admin")
                .setEmail("admin@example.test")
                .setPassword("CorrectHorse1"));

        assertFalse(result.isSuccess());
        assertEquals("302", result.getErrorContext().fetchRootError().getErrorCode().getErrorSpecific());
        verify(installationStore, never()).claimInitialInstallation();
        verify(userRepository, never()).save(any(), any());
    }
}
