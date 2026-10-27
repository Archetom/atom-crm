package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.LeadQueryRequest;
import io.github.archetom.crm.application.vo.LeadCollaboratorBaselineVO;
import io.github.archetom.crm.application.vo.LeadHierarchyVO;
import io.github.archetom.crm.application.vo.LeadVO;
import io.github.archetom.crm.application.vo.NearbyLeadVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.math.BigDecimal;
import java.util.List;

public interface LeadQueryService {
    Result<Pager<LeadVO>> query(AuthenticatedCaller caller, LeadQueryRequest request);
    Result<LeadVO> get(AuthenticatedCaller caller, Long leadId);
    Result<LeadCollaboratorBaselineVO> collaborators(AuthenticatedCaller caller, Long leadId);
    Result<LeadHierarchyVO> hierarchy(AuthenticatedCaller caller, Long leadId);
    Result<Boolean> duplicateCheck(AuthenticatedCaller caller, LeadDuplicateCheckRequest request);
    Result<Pager<NearbyLeadVO>> nearby(AuthenticatedCaller caller,
                                           BigDecimal longitude, BigDecimal latitude,
                                           Integer radiusMeters, Integer page, Integer size);
    Result<List<LeadVO>> summaries(AuthenticatedCaller caller, List<Long> ids);
    Result<List<String>> tags(AuthenticatedCaller caller);
}
