package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OpportunityQueryRequest;
import io.github.archetom.crm.application.vo.OpportunityContactVO;
import io.github.archetom.crm.application.vo.OpportunityContactsBaselineVO;
import io.github.archetom.crm.application.vo.OpportunityCollaboratorBaselineVO;
import io.github.archetom.crm.application.vo.OpportunityEditableBaselineVO;
import io.github.archetom.crm.application.vo.OpportunitySummaryVO;
import io.github.archetom.crm.application.vo.OpportunityVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

public interface OpportunityQueryService {
    Result<Pager<OpportunityVO>> query(
            AuthenticatedCaller caller, OpportunityQueryRequest request);
    Result<OpportunityVO> get(AuthenticatedCaller caller, Long opportunityId);
    Result<OpportunityEditableBaselineVO> editableBaseline(
            AuthenticatedCaller caller, Long opportunityId);
    Result<OpportunityCollaboratorBaselineVO> collaborators(
            AuthenticatedCaller caller, Long opportunityId);
    Result<OpportunityContactsBaselineVO> contactsBaseline(
            AuthenticatedCaller caller, Long opportunityId);
    Result<Pager<OpportunityContactVO>> contacts(
            AuthenticatedCaller caller, Long opportunityId, Integer page, Integer size);
    Result<OpportunitySummaryVO> summary(
            AuthenticatedCaller caller, OpportunityQueryRequest request);
}
