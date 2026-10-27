package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ConnectorCreateRequest;
import io.github.archetom.crm.api.dto.request.ConnectorQueryRequest;
import io.github.archetom.crm.api.dto.request.ConnectorStatusRequest;
import io.github.archetom.crm.api.dto.request.ConnectorVersionRequest;
import io.github.archetom.crm.application.vo.ConnectorVO;

public interface ConnectorService {
    Result<Pager<ConnectorVO>> query(AuthenticatedCaller caller, ConnectorQueryRequest request);
    Result<ConnectorVO> get(AuthenticatedCaller caller, Long connectorId);
    Result<ConnectorVO> create(AuthenticatedCaller caller, ConnectorCreateRequest request);
    Result<ConnectorVO> rotateCredential(
            AuthenticatedCaller caller, Long connectorId, ConnectorVersionRequest request);
    Result<ConnectorVO> revokeCredential(
            AuthenticatedCaller caller, Long connectorId, String keyId,
            ConnectorVersionRequest request);
    Result<ConnectorVO> updateStatus(
            AuthenticatedCaller caller, Long connectorId, ConnectorStatusRequest request);
}
