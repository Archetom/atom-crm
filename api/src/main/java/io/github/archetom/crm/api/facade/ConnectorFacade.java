package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ConnectorCreateRequest;
import io.github.archetom.crm.api.dto.request.ConnectorQueryRequest;
import io.github.archetom.crm.api.dto.request.ConnectorStatusRequest;
import io.github.archetom.crm.api.dto.request.ConnectorVersionRequest;
import io.github.archetom.crm.api.dto.response.ConnectorCredentialCreatedResponse;
import io.github.archetom.crm.api.dto.response.ConnectorPageResponse;
import io.github.archetom.crm.api.dto.response.ConnectorResponse;

public interface ConnectorFacade {
    Result<ConnectorPageResponse> query(AuthenticatedCaller caller, ConnectorQueryRequest request);
    Result<ConnectorResponse> get(AuthenticatedCaller caller, Long connectorId);
    Result<ConnectorCredentialCreatedResponse> create(
            AuthenticatedCaller caller, ConnectorCreateRequest request);
    Result<ConnectorCredentialCreatedResponse> rotateCredential(
            AuthenticatedCaller caller, Long connectorId, ConnectorVersionRequest request);
    Result<ConnectorResponse> revokeCredential(
            AuthenticatedCaller caller, Long connectorId, String keyId,
            ConnectorVersionRequest request);
    Result<ConnectorResponse> updateStatus(
            AuthenticatedCaller caller, Long connectorId, ConnectorStatusRequest request);
}
