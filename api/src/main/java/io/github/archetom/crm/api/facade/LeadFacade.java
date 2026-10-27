package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadBatchTagRequest;
import io.github.archetom.crm.api.dto.request.LeadBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.LeadBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadConvertRequest;
import io.github.archetom.crm.api.dto.request.LeadCreateRequest;
import io.github.archetom.crm.api.dto.request.LeadDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.LeadOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadQueryRequest;
import io.github.archetom.crm.api.dto.request.LeadStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadUpdateRequest;
import io.github.archetom.crm.api.dto.response.LeadCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.LeadConversionResponse;
import io.github.archetom.crm.api.dto.response.LeadDuplicateResponse;
import io.github.archetom.crm.api.dto.response.LeadHierarchyResponse;
import io.github.archetom.crm.api.dto.response.LeadResponse;
import io.github.archetom.crm.api.dto.response.NearbyLeadResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.math.BigDecimal;
import java.util.List;

public interface LeadFacade {
    Result<Pager<LeadResponse>> query(AuthenticatedCaller caller, LeadQueryRequest request);
    Result<LeadResponse> get(AuthenticatedCaller caller, Long leadId);
    Result<LeadResponse> create(AuthenticatedCaller caller, LeadCreateRequest request);
    Result<LeadConversionResponse> convert(AuthenticatedCaller caller, Long leadId,
                                           LeadConvertRequest request);
    Result<LeadResponse> update(AuthenticatedCaller caller, Long leadId, LeadUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long leadId, Long version);
    Result<Void> deleteBatch(AuthenticatedCaller caller, LeadBatchDeleteRequest request);
    Result<LeadResponse> updateState(AuthenticatedCaller caller, Long leadId,
                                         LeadStateUpdateRequest request);
    Result<LeadResponse> updateOwner(AuthenticatedCaller caller, Long leadId,
                                     LeadOwnerUpdateRequest request);
    Result<Void> updateOwners(AuthenticatedCaller caller, LeadBatchOwnerUpdateRequest request);
    Result<LeadResponse> replaceTags(AuthenticatedCaller caller, Long leadId,
                                         LeadTagsUpdateRequest request);
    Result<List<LeadResponse>> changeTags(AuthenticatedCaller caller,
                                              LeadBatchTagRequest request);
    Result<LeadCollaboratorBaselineResponse> collaborators(AuthenticatedCaller caller,
                                                            Long leadId);
    Result<LeadResponse> replaceCollaborators(AuthenticatedCaller caller, Long leadId,
                                                  LeadCollaboratorsUpdateRequest request);
    Result<Void> exitCollaborator(AuthenticatedCaller caller, Long leadId, Long version);
    Result<LeadResponse> updateParent(AuthenticatedCaller caller, Long leadId,
                                          LeadParentUpdateRequest request);
    Result<LeadHierarchyResponse> hierarchy(AuthenticatedCaller caller, Long leadId);
    Result<Void> setStarred(AuthenticatedCaller caller, Long leadId,
                            LeadStarUpdateRequest request);
    Result<LeadDuplicateResponse> duplicateCheck(AuthenticatedCaller caller,
                                                     LeadDuplicateCheckRequest request);
    Result<Pager<NearbyLeadResponse>> nearby(AuthenticatedCaller caller,
                                                 BigDecimal longitude, BigDecimal latitude,
                                                 Integer radiusMeters, Integer page, Integer size);
    Result<List<LeadResponse>> summaries(AuthenticatedCaller caller, List<Long> ids);
    Result<List<String>> tags(AuthenticatedCaller caller);
}
