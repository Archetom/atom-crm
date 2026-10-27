package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ConnectorCreateRequest;
import io.github.archetom.crm.api.dto.request.ConnectorQueryRequest;
import io.github.archetom.crm.api.dto.request.ConnectorStatusRequest;
import io.github.archetom.crm.api.dto.request.ConnectorVersionRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.ConnectorAuthenticationStateInvalidator;
import io.github.archetom.crm.application.security.ConnectorCredential;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.ConnectorService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.application.vo.ConnectorCredentialVO;
import io.github.archetom.crm.application.vo.ConnectorVO;
import io.github.archetom.crm.domain.entity.ConnectorRegistration;
import io.github.archetom.crm.domain.model.ConnectorQuery;
import io.github.archetom.crm.domain.model.ConnectorStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ConnectorRegistrationRepository;
import io.github.archetom.crm.domain.valueobject.ConnectorId;
import io.github.archetom.crm.domain.valueobject.ConnectorRef;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ConnectorServiceImpl implements ConnectorService {
    private final ConnectorRegistrationRepository repository;
    private final OrganizationAccessGuard accessGuard;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;
    private final AfterCommitExecutor afterCommitExecutor;
    private final ConnectorAuthenticationStateInvalidator authenticationStateInvalidator;

    @Override
    public Result<Pager<ConnectorVO>> query(
            AuthenticatedCaller caller, ConnectorQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.CONNECTOR_QUERY, new ServiceOperation<>() {
            private TenantId tenantId;
            private ConnectorStatus status;

            @Override
            public void validate() {
                requireQuery(request);
                tenantId = accessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.CONNECTORS_READ);
                status = optionalStatus(request.getStatus());
            }

            @Override
            public Pager<ConnectorVO> execute() {
                var page = repository.findPage(tenantId,
                        new ConnectorQuery(request.getPage(), request.getSize(), status));
                Pager<ConnectorVO> result = new Pager<>();
                result.setPageNum(page.page());
                result.setPageSize(page.size());
                result.setTotalNum(page.total());
                result.setObjectList(page.items().stream()
                        .map(value -> toVO(value, null)).toList());
                return result;
            }
        });
    }

    @Override
    public Result<ConnectorVO> get(AuthenticatedCaller caller, Long connectorId) {
        return queryTemplate.execute(UseCaseOperation.CONNECTOR_GET, new ServiceOperation<>() {
            private TenantId tenantId;
            private ConnectorId id;

            @Override
            public void validate() {
                id = connectorId(connectorId);
                tenantId = accessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.CONNECTORS_READ);
            }

            @Override
            public ConnectorVO execute() {
                return toVO(requireRegistration(tenantId, id, false), null);
            }
        });
    }

    @Override
    public Result<ConnectorVO> create(
            AuthenticatedCaller caller, ConnectorCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONNECTOR_CREATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private ConnectorCredential.Generated generated;

            @Override
            public void validate() {
                if (request == null) {
                    throw invalid("Connector create request must not be empty");
                }
                tenantId = accessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.CONNECTORS_MANAGE);
            }

            @Override
            public void prepare() {
                generated = ConnectorCredential.create();
            }

            @Override
            public ConnectorVO execute() {
                ConnectorRegistration registration = ConnectorRegistration.create(
                        tenantId, generated.connectorRef(), generated.keyId(),
                        generated.verifier(), now());
                return toVO(repository.save(tenantId, registration), generated.value());
            }
        });
    }

    @Override
    public Result<ConnectorVO> rotateCredential(
            AuthenticatedCaller caller, Long connectorId, ConnectorVersionRequest request) {
        return commandTemplate.execute(
                UseCaseOperation.CONNECTOR_CREDENTIAL_ROTATE, new ServiceOperation<>() {
                    private TenantId tenantId;
                    private ConnectorId id;
                    private ConnectorCredential.Generated generated;

                    @Override
                    public void validate() {
                        id = connectorId(connectorId);
                        requireVersion(request);
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.CONNECTORS_MANAGE);
                    }

                    @Override
                    public ConnectorVO execute() {
                        ConnectorRegistration registration = requireRegistration(tenantId, id, true);
                        requireVersion(registration, request.getVersion());
                        generated = ConnectorCredential.rotate(registration.getConnectorRef());
                        registration.rotateCredential(
                                generated.keyId(), generated.verifier(), now());
                        return toVO(repository.save(tenantId, registration), generated.value());
                    }
                });
    }

    @Override
    public Result<ConnectorVO> revokeCredential(
            AuthenticatedCaller caller, Long connectorId, String keyId,
            ConnectorVersionRequest request) {
        return commandTemplate.execute(
                UseCaseOperation.CONNECTOR_CREDENTIAL_REVOKE, new ServiceOperation<>() {
                    private TenantId tenantId;
                    private ConnectorId id;
                    private String credentialKeyId;
                    private ConnectorRef connectorRef;

                    @Override
                    public void validate() {
                        id = connectorId(connectorId);
                        requireVersion(request);
                        if (keyId == null || !keyId.matches("[A-Za-z0-9_-]{24}")) {
                            throw invalid("Connector credential key ID is invalid");
                        }
                        credentialKeyId = keyId;
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.CONNECTORS_MANAGE);
                    }

                    @Override
                    public ConnectorVO execute() {
                        ConnectorRegistration registration = requireRegistration(tenantId, id, true);
                        requireVersion(registration, request.getVersion());
                        connectorRef = registration.getConnectorRef();
                        registration.revokeCredential(credentialKeyId, now());
                        return toVO(repository.save(tenantId, registration), null);
                    }

                    @Override
                    public void onSuccess(ConnectorVO result) {
                        afterCommitExecutor.execute("connector authentication state invalidation", () -> authenticationStateInvalidator
                                .invalidateCredential(connectorRef, credentialKeyId));
                    }
                });
    }

    @Override
    public Result<ConnectorVO> updateStatus(
            AuthenticatedCaller caller, Long connectorId, ConnectorStatusRequest request) {
        return commandTemplate.execute(
                UseCaseOperation.CONNECTOR_STATUS_UPDATE, new ServiceOperation<>() {
                    private TenantId tenantId;
                    private ConnectorId id;
                    private ConnectorStatus target;
                    private ConnectorRef connectorRef;

                    @Override
                    public void validate() {
                        id = connectorId(connectorId);
                        if (request == null || request.getVersion() == null) {
                            throw invalid("Connector status request is invalid");
                        }
                        target = requiredStatus(request.getStatus());
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.CONNECTORS_MANAGE);
                    }

                    @Override
                    public ConnectorVO execute() {
                        ConnectorRegistration registration = requireRegistration(tenantId, id, true);
                        requireVersion(registration, request.getVersion());
                        connectorRef = registration.getConnectorRef();
                        registration.changeStatus(target, now());
                        return toVO(repository.save(tenantId, registration), null);
                    }

                    @Override
                    public void onSuccess(ConnectorVO result) {
                        if (target != ConnectorStatus.ACTIVE) {
                            afterCommitExecutor.execute("connector authentication state invalidation", () -> authenticationStateInvalidator
                                    .invalidateConnector(connectorRef));
                        }
                    }
                });
    }

    private ConnectorRegistration requireRegistration(
            TenantId tenantId, ConnectorId connectorId, boolean forUpdate) {
        var value = forUpdate ? repository.findByIdForUpdate(tenantId, connectorId)
                : repository.findById(tenantId, connectorId);
        return value.orElseThrow(() -> new NonRetryableApplicationException(
                ApplicationErrorCode.RESOURCE_NOT_FOUND,
                "Connector registration was not found"));
    }

    private ConnectorVO toVO(ConnectorRegistration value, String credential) {
        return new ConnectorVO(value.getId().getValue(), value.getConnectorRef().getValue(),
                value.getStatus().name(), value.getCredentialKeys().stream()
                .map(key -> new ConnectorCredentialVO(key.getKeyId(), key.getStatus().name(),
                        key.getCreatedTime(), key.getRevokedTime())).toList(),
                credential, value.getVersion(), value.getCreatedTime(), value.getUpdatedTime(),
                value.getRetiredTime());
    }

    private void requireQuery(ConnectorQueryRequest request) {
        if (request == null || request.getPage() == null || request.getSize() == null
                || request.getPage() < 1 || request.getPage() > QueryRequest.MAX_PAGE
                || request.getSize() < 1 || request.getSize() > QueryRequest.MAX_SIZE) {
            throw invalid("Connector query is invalid");
        }
    }

    private ConnectorStatus optionalStatus(String value) {
        return value == null || value.isBlank() ? null : requiredStatus(value);
    }

    private ConnectorStatus requiredStatus(String value) {
        try {
            return ConnectorStatus.valueOf(value == null ? ""
                    : value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw invalid("Connector status is invalid");
        }
    }

    private ConnectorId connectorId(Long value) {
        try {
            return new ConnectorId(value);
        } catch (RuntimeException exception) {
            throw invalid("Connector ID is invalid");
        }
    }

    private void requireVersion(ConnectorVersionRequest request) {
        if (request == null || request.getVersion() == null || request.getVersion() < 0) {
            throw invalid("Connector version is required");
        }
    }

    private void requireVersion(ConnectorRegistration registration, Long expected) {
        if (!registration.getVersion().equals(expected)) {
            throw new NonRetryableApplicationException(
                    ApplicationErrorCode.VERSION_CONFLICT,
                    "Connector registration version is stale");
        }
    }

    private NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, message);
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }
}
