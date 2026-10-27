package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerBatchTagRequest;
import io.github.archetom.crm.api.dto.request.CustomerCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.api.dto.request.CustomerDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.CustomerOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerQueryRequest;
import io.github.archetom.crm.api.dto.request.CustomerStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerUpdateRequest;
import io.github.archetom.crm.api.dto.response.CustomerCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.CustomerDuplicateResponse;
import io.github.archetom.crm.api.dto.response.CustomerHierarchyResponse;
import io.github.archetom.crm.api.dto.response.CustomerResponse;
import io.github.archetom.crm.api.dto.response.NearbyCustomerResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.math.BigDecimal;
import java.util.List;

public interface CustomerFacade {
    Result<Pager<CustomerResponse>> query(AuthenticatedCaller caller, CustomerQueryRequest request);
    Result<CustomerResponse> get(AuthenticatedCaller caller, Long customerId);
    Result<CustomerResponse> create(AuthenticatedCaller caller, CustomerCreateRequest request);
    Result<CustomerResponse> update(AuthenticatedCaller caller, Long customerId, CustomerUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long customerId, Long version);
    Result<CustomerResponse> updateState(AuthenticatedCaller caller, Long customerId,
                                         CustomerStateUpdateRequest request);
    Result<CustomerResponse> updateOwner(AuthenticatedCaller caller, Long customerId,
                                         CustomerOwnerUpdateRequest request);
    Result<CustomerResponse> replaceTags(AuthenticatedCaller caller, Long customerId,
                                         CustomerTagsUpdateRequest request);
    Result<List<CustomerResponse>> changeTags(AuthenticatedCaller caller,
                                              CustomerBatchTagRequest request);
    Result<CustomerCollaboratorBaselineResponse> collaborators(AuthenticatedCaller caller,
                                                               Long customerId);
    Result<CustomerResponse> replaceCollaborators(AuthenticatedCaller caller, Long customerId,
                                                  CustomerCollaboratorsUpdateRequest request);
    Result<Void> exitCollaborator(AuthenticatedCaller caller, Long customerId, Long version);
    Result<CustomerResponse> updateParent(AuthenticatedCaller caller, Long customerId,
                                          CustomerParentUpdateRequest request);
    Result<CustomerHierarchyResponse> hierarchy(AuthenticatedCaller caller, Long customerId);
    Result<Void> setStarred(AuthenticatedCaller caller, Long customerId,
                            CustomerStarUpdateRequest request);
    Result<CustomerDuplicateResponse> duplicateCheck(AuthenticatedCaller caller,
                                                     CustomerDuplicateCheckRequest request);
    Result<Pager<NearbyCustomerResponse>> nearby(AuthenticatedCaller caller,
                                                 BigDecimal longitude, BigDecimal latitude,
                                                 Integer radiusMeters, Integer page, Integer size);
    Result<List<CustomerResponse>> summaries(AuthenticatedCaller caller, List<Long> ids);
    Result<List<String>> tags(AuthenticatedCaller caller);
}
