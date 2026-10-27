package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadIntakeClientCreateRequest;
import io.github.archetom.crm.api.dto.request.LeadIntakeClientDeactivateRequest;
import io.github.archetom.crm.application.vo.LeadIntakeClientVO;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface LeadIntakeClientService {
    Result<List<LeadIntakeClientVO>> list(AuthenticatedCaller caller);
    Result<LeadIntakeClientVO> create(AuthenticatedCaller caller, LeadIntakeClientCreateRequest request);
    Result<LeadIntakeClientVO> deactivate(AuthenticatedCaller caller, Long clientId,
                                          LeadIntakeClientDeactivateRequest request);
}
