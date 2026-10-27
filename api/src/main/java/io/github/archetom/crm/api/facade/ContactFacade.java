package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactBatchTagRequest;
import io.github.archetom.crm.api.dto.request.ContactBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ContactBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactCreateRequest;
import io.github.archetom.crm.api.dto.request.ContactDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.ContactOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactQueryRequest;
import io.github.archetom.crm.api.dto.request.ContactStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactUpdateRequest;
import io.github.archetom.crm.api.dto.response.ContactCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.ContactDuplicateResponse;
import io.github.archetom.crm.api.dto.response.ContactHierarchyResponse;
import io.github.archetom.crm.api.dto.response.ContactResponse;
import io.github.archetom.crm.api.dto.response.NearbyContactResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.math.BigDecimal;
import java.util.List;

public interface ContactFacade {
    Result<Pager<ContactResponse>> query(AuthenticatedCaller caller, ContactQueryRequest request);
    Result<ContactResponse> get(AuthenticatedCaller caller, Long contactId);
    Result<ContactResponse> create(AuthenticatedCaller caller, ContactCreateRequest request);
    Result<ContactResponse> update(AuthenticatedCaller caller, Long contactId, ContactUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long contactId, Long version);
    Result<Void> deleteBatch(AuthenticatedCaller caller, ContactBatchDeleteRequest request);
    Result<ContactResponse> updateState(AuthenticatedCaller caller, Long contactId,
                                         ContactStateUpdateRequest request);
    Result<ContactResponse> updateOwner(AuthenticatedCaller caller, Long contactId,
                                         ContactOwnerUpdateRequest request);
    Result<Void> updateOwners(AuthenticatedCaller caller,
                              ContactBatchOwnerUpdateRequest request);
    Result<ContactResponse> replaceTags(AuthenticatedCaller caller, Long contactId,
                                         ContactTagsUpdateRequest request);
    Result<List<ContactResponse>> changeTags(AuthenticatedCaller caller,
                                              ContactBatchTagRequest request);
    Result<ContactCollaboratorBaselineResponse> collaborators(AuthenticatedCaller caller,
                                                               Long contactId);
    Result<ContactResponse> replaceCollaborators(AuthenticatedCaller caller, Long contactId,
                                                  ContactCollaboratorsUpdateRequest request);
    Result<Void> exitCollaborator(AuthenticatedCaller caller, Long contactId, Long version);
    Result<ContactResponse> updateParent(AuthenticatedCaller caller, Long contactId,
                                          ContactParentUpdateRequest request);
    Result<ContactHierarchyResponse> hierarchy(AuthenticatedCaller caller, Long contactId);
    Result<Void> setStarred(AuthenticatedCaller caller, Long contactId,
                            ContactStarUpdateRequest request);
    Result<ContactDuplicateResponse> duplicateCheck(AuthenticatedCaller caller,
                                                     ContactDuplicateCheckRequest request);
    Result<Pager<NearbyContactResponse>> nearby(AuthenticatedCaller caller,
                                                 BigDecimal longitude, BigDecimal latitude,
                                                 Integer radiusMeters, Integer page, Integer size);
    Result<List<ContactResponse>> summaries(AuthenticatedCaller caller, List<Long> ids);
    Result<List<String>> tags(AuthenticatedCaller caller);
}
