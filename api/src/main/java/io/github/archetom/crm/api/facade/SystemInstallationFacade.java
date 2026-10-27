package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.BrowserSessionIssuance;
import io.github.archetom.crm.api.dto.request.InitialTenantAdminRequest;
import io.github.archetom.crm.api.dto.response.InitialInstallationStatusResponse;

/** Public contract boundary for the one-time fresh-system installation. */
public interface SystemInstallationFacade {

    Result<InitialInstallationStatusResponse> installationStatus();

    Result<BrowserSessionIssuance> initializeTenantAdmin(InitialTenantAdminRequest request);
}
