package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.EnterpriseIdentifierLookupRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseNameSearchRequest;
import io.github.archetom.crm.application.vo.EnterpriseDataCapabilityVO;
import io.github.archetom.crm.application.vo.EnterpriseDataObservationVO;
import io.github.archetom.common.result.Result;

import java.util.List;

/** Human-triggered, tenant-bound enterprise-data read use cases. */
public interface EnterpriseDataQueryService {

    Result<EnterpriseDataCapabilityVO> capabilities(AuthenticatedCaller caller);

    Result<List<EnterpriseDataObservationVO>> search(
            AuthenticatedCaller caller, EnterpriseNameSearchRequest request);

    Result<EnterpriseDataObservationVO> lookup(
            AuthenticatedCaller caller, EnterpriseIdentifierLookupRequest request);
}
