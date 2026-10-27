package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactBatchTagRequest;
import io.github.archetom.crm.api.dto.request.ContactBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ContactBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactCreateRequest;
import io.github.archetom.crm.api.dto.request.ContactOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactUpdateRequest;
import io.github.archetom.crm.application.vo.ContactVO;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface ContactCommandService {
    Result<ContactVO> create(AuthenticatedCaller caller, ContactCreateRequest request);
    Result<ContactVO> update(AuthenticatedCaller caller, Long contactId, ContactUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long contactId, Long version);
    Result<Void> deleteBatch(AuthenticatedCaller caller, ContactBatchDeleteRequest request);
    Result<ContactVO> updateState(AuthenticatedCaller caller, Long contactId,
                                   ContactStateUpdateRequest request);
    Result<ContactVO> updateOwner(AuthenticatedCaller caller, Long contactId,
                                   ContactOwnerUpdateRequest request);
    Result<Void> updateOwners(AuthenticatedCaller caller,
                              ContactBatchOwnerUpdateRequest request);
    Result<ContactVO> replaceTags(AuthenticatedCaller caller, Long contactId,
                                   ContactTagsUpdateRequest request);
    Result<List<ContactVO>> changeTags(AuthenticatedCaller caller, ContactBatchTagRequest request);
    Result<ContactVO> replaceCollaborators(AuthenticatedCaller caller, Long contactId,
                                            ContactCollaboratorsUpdateRequest request);
    Result<Void> exitCollaborator(AuthenticatedCaller caller, Long contactId, Long version);
    Result<ContactVO> updateParent(AuthenticatedCaller caller, Long contactId,
                                    ContactParentUpdateRequest request);
    Result<Void> setStarred(AuthenticatedCaller caller, Long contactId,
                            ContactStarUpdateRequest request);
}
