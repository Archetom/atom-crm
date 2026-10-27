package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadIntakeClientCreateRequest;
import io.github.archetom.crm.api.dto.request.LeadIntakeClientDeactivateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.LeadIntakeRateLimitInvalidator;
import io.github.archetom.crm.application.security.LeadIntakeApiKey;
import io.github.archetom.crm.application.security.LeadAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.LeadIntakeClientService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.application.vo.LeadIntakeClientVO;
import io.github.archetom.crm.domain.entity.LeadIntakeClient;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.repository.LeadIntakeClientRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.LeadIntakeClientId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeadIntakeClientServiceImpl implements LeadIntakeClientService {

    private final LeadIntakeClientRepository repository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationAccessGuard accessGuard;
    private final LeadAccessResolver leadAccessResolver;
    private final LeadApplicationSupport leadSupport;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;
    private final AfterCommitExecutor afterCommitExecutor;
    private final LeadIntakeRateLimitInvalidator rateLimitInvalidator;

    @Override
    public Result<List<LeadIntakeClientVO>> list(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.LEAD_INTAKE_CLIENT_LIST,
                new ServiceOperation<>() {
                    private TenantId tenantId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.LEADS_INTAKE_MANAGE);
                    }

                    @Override
                    public List<LeadIntakeClientVO> execute() {
                        return repository.findAll(tenantId).stream()
                                .map(value -> toVO(value, null)).toList();
                    }
                });
    }

    @Override
    public Result<LeadIntakeClientVO> create(
            AuthenticatedCaller caller, LeadIntakeClientCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.LEAD_INTAKE_CLIENT_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.LEADS_INTAKE_MANAGE);
                        memberId = new MemberId(caller.actorId());
                        if (request == null) {
                            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Lead intake client request must not be empty");
                        }
                    }

                    @Override
                    public LeadIntakeClientVO execute() {
                        requireActiveMember(tenantId, memberId);
                        requirePublicSubmissionCapability(caller, tenantId, memberId);
                        LeadIntakeApiKey.Generated key = LeadIntakeApiKey.generate(tenantId);
                        LeadIntakeClient client = LeadIntakeClient.create(tenantId, memberId,
                                key.keyId(), LeadIntakeApiKey.sha256(key.secret()));
                        return toVO(repository.save(tenantId, client), key.value());
                    }
                });
    }

    @Override
    public Result<LeadIntakeClientVO> deactivate(
            AuthenticatedCaller caller, Long clientId, LeadIntakeClientDeactivateRequest request) {
        return commandTemplate.execute(UseCaseOperation.LEAD_INTAKE_CLIENT_DEACTIVATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private LeadIntakeClientId id;
                    private String deactivatedKeyId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.LEADS_INTAKE_MANAGE);
                        try {
                            id = new LeadIntakeClientId(clientId);
                        } catch (RuntimeException exception) {
                            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Lead intake client ID is invalid");
                        }
                        if (request == null || request.getVersion() == null) {
                            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Lead intake client version is required");
                        }
                    }

                    @Override
                    public LeadIntakeClientVO execute() {
                        LeadIntakeClient client = repository.findById(tenantId, id)
                                .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                        "Lead intake client was not found"));
                        if (!request.getVersion().equals(client.getVersion())) {
                            throw error(ApplicationErrorCode.VERSION_CONFLICT,
                                    "Lead intake client version is stale");
                        }
                        deactivatedKeyId = client.getKeyId();
                        client.deactivate();
                        return toVO(repository.save(tenantId, client), null);
                    }

                    @Override
                    public void onSuccess(LeadIntakeClientVO ignored) {
                        afterCommitExecutor.execute("lead intake client credential invalidation",
                                () -> rateLimitInvalidator.invalidate(tenantId, deactivatedKeyId));
                    }
                });
    }

    private void requireActiveMember(TenantId tenantId, MemberId memberId) {
        membershipRepository.findByMember(tenantId, memberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> error(ApplicationErrorCode.ACCESS_DENIED,
                        "Lead intake client owner is not an active member"));
    }

    private void requirePublicSubmissionCapability(
            AuthenticatedCaller caller, TenantId tenantId, MemberId memberId) {
        LeadAccessResolver.LeadAccess access = leadAccessResolver.access(
                caller, AuthorityCatalog.LEADS_WRITE, LeadAccessMode.WRITE);
        if (!tenantId.equals(access.tenantId()) || !memberId.equals(access.memberId())) {
            throw error(ApplicationErrorCode.ACCESS_DENIED,
                    "Lead intake client owner cannot submit leads");
        }
        var fieldAccess = leadSupport.accesses(tenantId, memberId);
        leadSupport.requireWrite(fieldAccess, LeadFieldCatalog.NAME);
        leadSupport.requireWrite(fieldAccess, LeadFieldCatalog.OWNER_ACTOR);
    }

    private LeadIntakeClientVO toVO(LeadIntakeClient client, String apiKey) {
        return new LeadIntakeClientVO(client.getId().getValue(), client.getKeyId(), apiKey,
                client.getOwnerMemberId().getValue(), client.isActive(), client.getVersion(),
                client.getCreatedTime(), client.getUpdatedTime());
    }

    private NonRetryableApplicationException error(ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }
}
