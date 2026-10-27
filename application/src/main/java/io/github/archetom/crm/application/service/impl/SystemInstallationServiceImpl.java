package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.InitialTenantAdminRequest;
import io.github.archetom.crm.application.assembler.UserAssembler;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.authentication.AuthenticationTokenCodec;
import io.github.archetom.crm.application.port.out.installation.SystemInstallationStore;
import io.github.archetom.crm.application.security.BrowserSession;
import io.github.archetom.crm.application.security.TenantAuthorizationChangeNotifier;
import io.github.archetom.crm.application.service.SystemInstallationService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.application.vo.BrowserSessionVO;
import io.github.archetom.crm.application.vo.InitialInstallationStatusVO;
import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.User;
import io.github.archetom.crm.domain.event.DomainEventPublisher;
import io.github.archetom.crm.domain.factory.UserFactory;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.UserRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Creates exactly one local tenant owner before a deployment has any initialized tenant.
 * The generated tenant is deliberately fixed at ID {@value #INITIAL_TENANT_ID_VALUE}; later
 * access remains subject to the ordinary browser password-login and membership checks.
 */
@Service
@RequiredArgsConstructor
public class SystemInstallationServiceImpl implements SystemInstallationService {

    private static final long INITIAL_TENANT_ID_VALUE = 1L;
    private static final TenantId INITIAL_TENANT_ID = new TenantId(INITIAL_TENANT_ID_VALUE);

    private final SystemInstallationStore installationStore;
    private final UserFactory userFactory;
    private final UserRepository userRepository;
    private final OrganizationTenantRepository organizationTenantRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final SystemRoleProvisioner systemRoleProvisioner;
    private final CustomerFieldProvisioner customerFieldProvisioner;
    private final LeadFieldProvisioner leadFieldProvisioner;
    private final ContactFieldProvisioner contactFieldProvisioner;
    private final OpportunityFieldProvisioner opportunityFieldProvisioner;
    private final OpportunityPipelineProvisioner opportunityPipelineProvisioner;
    private final ProductFieldProvisioner productFieldProvisioner;
    private final ContractFieldProvisioner contractFieldProvisioner;
    private final InvoiceFieldProvisioner invoiceFieldProvisioner;
    private final ReceivableFieldProvisioner receivableFieldProvisioner;
    private final ReceivablePlanFieldProvisioner receivablePlanFieldProvisioner;
    private final ReturnVisitFieldProvisioner returnVisitFieldProvisioner;
    private final OaRequestFieldProvisioner oaRequestFieldProvisioner;
    private final TenantAuthorizationChangeNotifier authorizationChangeNotifier;
    private final AuthenticationTokenCodec tokenCodec;
    private final DomainEventPublisher domainEventPublisher;
    private final UserCacheService userCacheService;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;
    private final AfterCommitExecutor afterCommitExecutor;

    @Value("${atom.authentication.session-ttl:PT8H}")
    private Duration sessionTtl;

    @Override
    public Result<InitialInstallationStatusVO> installationStatus() {
        return queryTemplate.execute(UseCaseOperation.SYSTEM_INSTALLATION_STATUS_GET, new ServiceOperation<>() {
            @Override
            public InitialInstallationStatusVO execute() {
                return new InitialInstallationStatusVO(installationStore.installationRequired());
            }
        });
    }

    @Override
    public Result<BrowserSessionVO> initializeTenantAdmin(InitialTenantAdminRequest request) {
        return commandTemplate.execute(UseCaseOperation.SYSTEM_INSTALLATION_TENANT_ADMIN_CREATE,
                new ServiceOperation<>() {
                    private User administrator;

                    @Override
                    public void validate() {
                        if (request == null || request.getDisplayName() == null || request.getDisplayName().isBlank()
                                || request.getLoginName() == null || request.getEmail() == null
                                || request.getPassword() == null) {
                            throw invalidRequest();
                        }
                    }

                    @Override
                    public void prepare() {
                        try {
                            administrator = userFactory.createInitialAdministrator(INITIAL_TENANT_ID,
                                    request.getLoginName(), request.getEmail(), request.getPassword(),
                                    request.getDisplayName());
                        } catch (IllegalArgumentException exception) {
                            throw invalidRequest(exception);
                        }
                    }

                    @Override
                    public BrowserSessionVO execute() {
                        if (!installationStore.installationRequired()
                                || !installationStore.claimInitialInstallation()) {
                            throw installationCompleted();
                        }
                        administrator = userRepository.save(INITIAL_TENANT_ID, administrator);
                        MemberId administratorId = new MemberId(administrator.getId().getValue());
                        if (!organizationTenantRepository.initialize(INITIAL_TENANT_ID, administratorId)) {
                            throw installationCompleted();
                        }
                        memberProfileRepository.save(MemberProfile.register(administratorId,
                                request.getDisplayName(), administrator.getEmail(), administrator.getPhoneNumber()));
                        membershipRepository.save(INITIAL_TENANT_ID,
                                OrganizationMembership.activate(INITIAL_TENANT_ID, administratorId, null));
                        systemRoleProvisioner.provisionOwner(INITIAL_TENANT_ID, administratorId);
                        provisionTenantDefaults();
                        authorizationChangeNotifier.changed(INITIAL_TENANT_ID);

                        Instant expiresAt = Instant.now().plus(validSessionTtl());
                        String token = tokenCodec.issueBrowserSession(new BrowserSession(
                                administrator.getId().getValue(), INITIAL_TENANT_ID_VALUE,
                                INITIAL_TENANT_ID_VALUE, administrator.getVersion(), expiresAt));
                        return new BrowserSessionVO(administrator.getId().getValue(), INITIAL_TENANT_ID_VALUE,
                                expiresAt, token);
                    }

                    @Override
                    public void onSuccess(BrowserSessionVO ignored) {
                        afterCommitExecutor.execute("installation domain events", () ->
                                domainEventPublisher.publishAll(administrator.pullDomainEvents()));
                        afterCommitExecutor.execute("installation cache warm-up", () ->
                                userCacheService.cacheUser(INITIAL_TENANT_ID,
                                        UserAssembler.INSTANCE.toVO(administrator)));
                    }
                });
    }

    private void provisionTenantDefaults() {
        customerFieldProvisioner.provision(INITIAL_TENANT_ID);
        leadFieldProvisioner.provision(INITIAL_TENANT_ID);
        contactFieldProvisioner.provision(INITIAL_TENANT_ID);
        opportunityFieldProvisioner.provision(INITIAL_TENANT_ID);
        opportunityPipelineProvisioner.provision(INITIAL_TENANT_ID);
        productFieldProvisioner.provision(INITIAL_TENANT_ID);
        contractFieldProvisioner.provision(INITIAL_TENANT_ID);
        invoiceFieldProvisioner.provision(INITIAL_TENANT_ID);
        receivableFieldProvisioner.provision(INITIAL_TENANT_ID);
        receivablePlanFieldProvisioner.provision(INITIAL_TENANT_ID);
        returnVisitFieldProvisioner.provision(INITIAL_TENANT_ID);
        oaRequestFieldProvisioner.provision(INITIAL_TENANT_ID);
    }

    private Duration validSessionTtl() {
        if (sessionTtl == null || sessionTtl.isNegative() || sessionTtl.isZero()) {
            throw new IllegalStateException("session duration must be positive");
        }
        return sessionTtl;
    }

    private static NonRetryableApplicationException invalidRequest() {
        return invalidRequest(null);
    }

    private static NonRetryableApplicationException invalidRequest(Exception cause) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID,
                "Initial tenant administrator request is invalid", cause);
    }

    private static NonRetryableApplicationException installationCompleted() {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_ALREADY_EXISTS,
                "This deployment has already been initialized");
    }
}
