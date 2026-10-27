package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadIntakeClientCreateRequest;
import io.github.archetom.crm.api.dto.request.LeadIntakeClientDeactivateRequest;
import io.github.archetom.crm.api.dto.response.LeadIntakeClientCreatedResponse;
import io.github.archetom.crm.api.dto.response.LeadIntakeClientResponse;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface LeadIntakeClientFacade {
    Result<List<LeadIntakeClientResponse>> list(AuthenticatedCaller caller);
    Result<LeadIntakeClientCreatedResponse> create(AuthenticatedCaller caller,
                                                    LeadIntakeClientCreateRequest request);
    Result<LeadIntakeClientResponse> deactivate(AuthenticatedCaller caller, Long clientId,
                                                 LeadIntakeClientDeactivateRequest request);
}
