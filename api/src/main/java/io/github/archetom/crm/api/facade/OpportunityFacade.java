package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OpportunityBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.OpportunityBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityContactsUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityOutcomeUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPrimaryContactUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityQueryRequest;
import io.github.archetom.crm.api.dto.request.OpportunityStageUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityProductsUpdateRequest;
import io.github.archetom.crm.api.dto.response.OpportunityContactResponse;
import io.github.archetom.crm.api.dto.response.OpportunityCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.OpportunityContactsBaselineResponse;
import io.github.archetom.crm.api.dto.response.OpportunityEditableBaselineResponse;
import io.github.archetom.crm.api.dto.response.OpportunityResponse;
import io.github.archetom.crm.api.dto.response.OpportunitySummaryResponse;
import io.github.archetom.crm.api.dto.response.OpportunityProductsResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

public interface OpportunityFacade {
    Result<Pager<OpportunityResponse>> query(
            AuthenticatedCaller caller, OpportunityQueryRequest request);
    Result<OpportunityResponse> get(
            AuthenticatedCaller caller, Long opportunityId);
    Result<OpportunityEditableBaselineResponse> editableBaseline(
            AuthenticatedCaller caller, Long opportunityId);
    Result<OpportunityCollaboratorBaselineResponse> collaborators(
            AuthenticatedCaller caller, Long opportunityId);
    Result<OpportunityContactsBaselineResponse> contactsBaseline(
            AuthenticatedCaller caller, Long opportunityId);
    Result<OpportunityResponse> create(
            AuthenticatedCaller caller, OpportunityCreateRequest request);
    Result<OpportunityResponse> update(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityUpdateRequest request);
    Result<Void> delete(
            AuthenticatedCaller caller, Long opportunityId, Long version);
    Result<Void> deleteBatch(AuthenticatedCaller caller, OpportunityBatchDeleteRequest request);
    Result<OpportunityResponse> updateOwner(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityOwnerUpdateRequest request);
    Result<Void> updateOwners(
            AuthenticatedCaller caller, OpportunityBatchOwnerUpdateRequest request);
    Result<OpportunityResponse> replaceCollaborators(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityCollaboratorsUpdateRequest request);
    Result<Void> exitCollaborator(
            AuthenticatedCaller caller, Long opportunityId, Long version);
    Result<OpportunityResponse> replaceContacts(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityContactsUpdateRequest request);
    Result<OpportunityResponse> updatePrimaryContact(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityPrimaryContactUpdateRequest request);
    Result<OpportunityResponse> updateStage(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityStageUpdateRequest request);
    Result<OpportunityResponse> updateOutcome(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityOutcomeUpdateRequest request);
    Result<Void> setStarred(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityStarUpdateRequest request);
    Result<Pager<OpportunityContactResponse>> contacts(
            AuthenticatedCaller caller, Long opportunityId, Integer page, Integer size);
    Result<OpportunitySummaryResponse> summary(
            AuthenticatedCaller caller, OpportunityQueryRequest request);
    Result<OpportunityProductsResponse> products(
            AuthenticatedCaller caller, Long opportunityId);
    Result<OpportunityProductsResponse> replaceProducts(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityProductsUpdateRequest request);
}
