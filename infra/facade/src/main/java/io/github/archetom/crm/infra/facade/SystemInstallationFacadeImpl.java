package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.BrowserSessionIssuance;
import io.github.archetom.crm.api.dto.request.InitialTenantAdminRequest;
import io.github.archetom.crm.api.dto.response.InitialInstallationStatusResponse;
import io.github.archetom.crm.api.facade.SystemInstallationFacade;
import io.github.archetom.crm.application.service.SystemInstallationService;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemInstallationFacadeImpl implements SystemInstallationFacade {

    private final SystemInstallationService service;

    @Override
    public Result<InitialInstallationStatusResponse> installationStatus() {
        return ResultUtil.map(service.installationStatus(), status ->
                new InitialInstallationStatusResponse()
                        .setInstallationRequired(status.installationRequired()));
    }

    @Override
    public Result<BrowserSessionIssuance> initializeTenantAdmin(InitialTenantAdminRequest request) {
        return ResultUtil.map(service.initializeTenantAdmin(request),
                AuthenticationFacadeImpl::issuance);
    }
}
