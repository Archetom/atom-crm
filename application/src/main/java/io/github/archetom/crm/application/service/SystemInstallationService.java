package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.InitialTenantAdminRequest;
import io.github.archetom.crm.application.vo.BrowserSessionVO;
import io.github.archetom.crm.application.vo.InitialInstallationStatusVO;

/** Public first-installation use case for a previously uninitialized local deployment. */
public interface SystemInstallationService {

    Result<InitialInstallationStatusVO> installationStatus();

    Result<BrowserSessionVO> initializeTenantAdmin(InitialTenantAdminRequest request);
}
