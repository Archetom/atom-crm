package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ConnectorCreateRequest;
import io.github.archetom.crm.api.dto.request.ConnectorQueryRequest;
import io.github.archetom.crm.api.dto.request.ConnectorStatusRequest;
import io.github.archetom.crm.api.dto.request.ConnectorVersionRequest;
import io.github.archetom.crm.api.dto.response.ConnectorCredentialCreatedResponse;
import io.github.archetom.crm.api.dto.response.ConnectorCredentialResponse;
import io.github.archetom.crm.api.dto.response.ConnectorPageResponse;
import io.github.archetom.crm.api.dto.response.ConnectorResponse;
import io.github.archetom.crm.api.facade.ConnectorFacade;
import io.github.archetom.crm.application.service.ConnectorService;
import io.github.archetom.crm.application.vo.ConnectorVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConnectorFacadeImpl implements ConnectorFacade {
    private final ConnectorService service;

    @Override
    public Result<ConnectorPageResponse> query(
            AuthenticatedCaller caller, ConnectorQueryRequest request) {
        return ResultUtil.map(service.query(caller, request), page -> {
            ConnectorPageResponse result = new ConnectorPageResponse();
            result.setPageNum(Math.toIntExact(page.getPageNum()));
            result.setPageSize(Math.toIntExact(page.getPageSize()));
            result.setTotalNum(page.getTotalNum());
            result.setObjectList(page.getObjectList().stream().map(this::response).toList());
            return result;
        });
    }

    @Override
    public Result<ConnectorResponse> get(AuthenticatedCaller caller, Long connectorId) {
        return ResultUtil.map(service.get(caller, connectorId), this::response);
    }

    @Override
    public Result<ConnectorCredentialCreatedResponse> create(
            AuthenticatedCaller caller, ConnectorCreateRequest request) {
        return ResultUtil.map(service.create(caller, request), this::credentialCreatedResponse);
    }

    @Override
    public Result<ConnectorCredentialCreatedResponse> rotateCredential(
            AuthenticatedCaller caller, Long connectorId, ConnectorVersionRequest request) {
        return ResultUtil.map(service.rotateCredential(caller, connectorId, request),
                this::credentialCreatedResponse);
    }

    @Override
    public Result<ConnectorResponse> revokeCredential(
            AuthenticatedCaller caller, Long connectorId, String keyId,
            ConnectorVersionRequest request) {
        return ResultUtil.map(service.revokeCredential(caller, connectorId, keyId, request),
                this::response);
    }

    @Override
    public Result<ConnectorResponse> updateStatus(
            AuthenticatedCaller caller, Long connectorId, ConnectorStatusRequest request) {
        return ResultUtil.map(service.updateStatus(caller, connectorId, request), this::response);
    }

    private ConnectorResponse response(ConnectorVO value) {
        return new ConnectorResponse().setId(value.id()).setConnectorRef(value.connectorRef())
                .setStatus(value.status())
                .setCredentials(value.credentials().stream().map(item ->
                        new ConnectorCredentialResponse().setKeyId(item.keyId())
                                .setStatus(item.status()).setCreatedTime(item.createdTime())
                                .setRevokedTime(item.revokedTime())).toList())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime()).setRetiredTime(value.retiredTime());
    }

    private ConnectorCredentialCreatedResponse credentialCreatedResponse(ConnectorVO value) {
        return new ConnectorCredentialCreatedResponse().setConnector(response(value))
                .setCredential(value.credential());
    }
}
