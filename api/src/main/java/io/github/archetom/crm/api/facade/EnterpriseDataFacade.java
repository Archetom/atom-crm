package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.EnterpriseIdentifierLookupRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseNameSearchRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseDataAdoptionPreviewRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseDataAdoptRequest;
import io.github.archetom.crm.api.dto.response.EnterpriseDataAdoptionPreviewResponse;
import io.github.archetom.crm.api.dto.response.EnterpriseDataAdoptionResponse;
import io.github.archetom.crm.api.dto.response.EnterpriseDataCapabilityResponse;
import io.github.archetom.crm.api.dto.response.EnterpriseDataObservationResponse;
import io.github.archetom.common.result.Result;

import java.util.List;

/** Public human-facing boundary for bounded enterprise-data lookup. */
public interface EnterpriseDataFacade {

    Result<EnterpriseDataCapabilityResponse> capabilities(AuthenticatedCaller caller);

    Result<List<EnterpriseDataObservationResponse>> search(
            AuthenticatedCaller caller, EnterpriseNameSearchRequest request);

    Result<EnterpriseDataObservationResponse> lookup(
            AuthenticatedCaller caller, EnterpriseIdentifierLookupRequest request);

    Result<EnterpriseDataAdoptionPreviewResponse> previewAdoption(
            AuthenticatedCaller caller, EnterpriseDataAdoptionPreviewRequest request);

    Result<EnterpriseDataAdoptionResponse> adopt(
            AuthenticatedCaller caller, EnterpriseDataAdoptRequest request);
}
