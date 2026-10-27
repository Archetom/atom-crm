package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadIntakeClientCreateRequest;
import io.github.archetom.crm.api.dto.request.LeadIntakeClientDeactivateRequest;
import io.github.archetom.crm.api.dto.response.LeadIntakeClientCreatedResponse;
import io.github.archetom.crm.api.dto.response.LeadIntakeClientResponse;
import io.github.archetom.crm.api.facade.LeadIntakeClientFacade;
import io.github.archetom.crm.application.service.LeadIntakeClientService;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeadIntakeClientFacadeImpl implements LeadIntakeClientFacade {

    private final LeadIntakeClientService service;

    @Override
    public Result<List<LeadIntakeClientResponse>> list(AuthenticatedCaller caller) {
        return ResultUtil.map(service.list(caller), values -> values.stream()
                .map(this::response).toList());
    }

    @Override
    public Result<LeadIntakeClientCreatedResponse> create(
            AuthenticatedCaller caller, LeadIntakeClientCreateRequest request) {
        return ResultUtil.map(service.create(caller, request), value ->
                new LeadIntakeClientCreatedResponse().setClient(response(value))
                        .setApiKey(value.apiKey()));
    }

    @Override
    public Result<LeadIntakeClientResponse> deactivate(
            AuthenticatedCaller caller, Long clientId, LeadIntakeClientDeactivateRequest request) {
        return ResultUtil.map(service.deactivate(caller, clientId, request), this::response);
    }

    private LeadIntakeClientResponse response(
            io.github.archetom.crm.application.vo.LeadIntakeClientVO value) {
        return new LeadIntakeClientResponse().setId(value.id()).setKeyId(value.keyId())
                .setOwnerActorId(value.ownerActorId()).setActive(value.active())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }
}
