package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadBatchTagRequest;
import io.github.archetom.crm.api.dto.request.LeadBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.LeadBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadConvertRequest;
import io.github.archetom.crm.application.vo.LeadConversionVO;
import io.github.archetom.crm.api.dto.request.LeadCreateRequest;
import io.github.archetom.crm.api.dto.request.LeadOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadUpdateRequest;
import io.github.archetom.crm.application.vo.LeadVO;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface LeadCommandService {
    Result<LeadVO> create(AuthenticatedCaller caller, LeadCreateRequest request);
    Result<LeadConversionVO> convert(AuthenticatedCaller caller, Long leadId,
                                     LeadConvertRequest request);
    Result<LeadVO> update(AuthenticatedCaller caller, Long leadId, LeadUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long leadId, Long version);
    Result<Void> deleteBatch(AuthenticatedCaller caller, LeadBatchDeleteRequest request);
    Result<LeadVO> updateState(AuthenticatedCaller caller, Long leadId,
                                   LeadStateUpdateRequest request);
    Result<LeadVO> updateOwner(AuthenticatedCaller caller, Long leadId,
                                   LeadOwnerUpdateRequest request);
    Result<Void> updateOwners(AuthenticatedCaller caller, LeadBatchOwnerUpdateRequest request);
    Result<LeadVO> replaceTags(AuthenticatedCaller caller, Long leadId,
                                   LeadTagsUpdateRequest request);
    Result<List<LeadVO>> changeTags(AuthenticatedCaller caller, LeadBatchTagRequest request);
    Result<LeadVO> replaceCollaborators(AuthenticatedCaller caller, Long leadId,
                                            LeadCollaboratorsUpdateRequest request);
    Result<Void> exitCollaborator(AuthenticatedCaller caller, Long leadId, Long version);
    Result<LeadVO> updateParent(AuthenticatedCaller caller, Long leadId,
                                    LeadParentUpdateRequest request);
    Result<Void> setStarred(AuthenticatedCaller caller, Long leadId,
                            LeadStarUpdateRequest request);
}
