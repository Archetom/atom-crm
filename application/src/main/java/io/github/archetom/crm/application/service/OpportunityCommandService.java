package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OpportunityBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.OpportunityBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityContactsUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityOutcomeUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPrimaryContactUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityStageUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityUpdateRequest;
import io.github.archetom.crm.application.vo.OpportunityVO;
import io.github.archetom.common.result.Result;

public interface OpportunityCommandService {
    Result<OpportunityVO> create(
            AuthenticatedCaller caller, OpportunityCreateRequest request);
    Result<OpportunityVO> update(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityUpdateRequest request);
    Result<Void> delete(
            AuthenticatedCaller caller, Long opportunityId, Long version);
    Result<Void> deleteBatch(AuthenticatedCaller caller, OpportunityBatchDeleteRequest request);
    Result<OpportunityVO> updateOwner(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityOwnerUpdateRequest request);
    Result<Void> updateOwners(
            AuthenticatedCaller caller, OpportunityBatchOwnerUpdateRequest request);
    Result<OpportunityVO> replaceCollaborators(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityCollaboratorsUpdateRequest request);
    Result<Void> exitCollaborator(
            AuthenticatedCaller caller, Long opportunityId, Long version);
    Result<OpportunityVO> replaceContacts(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityContactsUpdateRequest request);
    Result<OpportunityVO> updatePrimaryContact(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityPrimaryContactUpdateRequest request);
    Result<OpportunityVO> updateStage(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityStageUpdateRequest request);
    Result<OpportunityVO> updateOutcome(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityOutcomeUpdateRequest request);
    Result<Void> setStarred(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityStarUpdateRequest request);
}
