package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactPoolAssignRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolCreateRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolContactBatchRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolContactQueryRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolTransferRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolUpdateRequest;
import io.github.archetom.crm.api.dto.response.ContactPoolResponse;
import io.github.archetom.crm.api.dto.response.ContactResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface ContactPoolFacade {
    Result<Pager<ContactPoolResponse>> query(AuthenticatedCaller caller, Integer page, Integer size);
    Result<List<ContactPoolResponse>> catalog(AuthenticatedCaller caller);
    Result<ContactPoolResponse> get(AuthenticatedCaller caller, Long poolId);
    Result<ContactPoolResponse> create(AuthenticatedCaller caller, ContactPoolCreateRequest request);
    Result<ContactPoolResponse> update(AuthenticatedCaller caller, Long poolId, ContactPoolUpdateRequest request);
    Result<ContactPoolResponse> updateStatus(AuthenticatedCaller caller, Long poolId,
                                              ContactPoolStatusUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long poolId, Long version);
    Result<Void> transfer(AuthenticatedCaller caller, Long poolId, ContactPoolTransferRequest request);
    Result<Pager<ContactResponse>> contacts(AuthenticatedCaller caller, Long poolId,
                                              ContactPoolContactQueryRequest request);
    Result<ContactResponse> contact(AuthenticatedCaller caller, Long poolId, Long contactId);
    Result<List<ContactResponse>> release(AuthenticatedCaller caller, Long poolId,
                                           ContactPoolContactBatchRequest request);
    Result<List<ContactResponse>> claim(AuthenticatedCaller caller, Long poolId,
                                         ContactPoolContactBatchRequest request);
    Result<List<ContactResponse>> assign(AuthenticatedCaller caller, Long poolId,
                                          ContactPoolAssignRequest request);
    Result<Void> deleteContacts(AuthenticatedCaller caller, Long poolId,
                                 ContactPoolContactBatchRequest request);
}
