package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactPoolAssignRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolCreateRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolContactBatchRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolContactQueryRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolTransferRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolUpdateRequest;
import io.github.archetom.crm.application.vo.ContactPoolVO;
import io.github.archetom.crm.application.vo.ContactVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface ContactPoolService {
    Result<Pager<ContactPoolVO>> query(AuthenticatedCaller caller, Integer page, Integer size);
    Result<List<ContactPoolVO>> catalog(AuthenticatedCaller caller);
    Result<ContactPoolVO> get(AuthenticatedCaller caller, Long poolId);
    Result<ContactPoolVO> create(AuthenticatedCaller caller, ContactPoolCreateRequest request);
    Result<ContactPoolVO> update(AuthenticatedCaller caller, Long poolId, ContactPoolUpdateRequest request);
    Result<ContactPoolVO> updateStatus(AuthenticatedCaller caller, Long poolId,
                                        ContactPoolStatusUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long poolId, Long version);
    Result<Void> transfer(AuthenticatedCaller caller, Long poolId, ContactPoolTransferRequest request);
    Result<Pager<ContactVO>> contacts(AuthenticatedCaller caller, Long poolId,
                                        ContactPoolContactQueryRequest request);
    Result<ContactVO> contact(AuthenticatedCaller caller, Long poolId, Long contactId);
    Result<List<ContactVO>> release(AuthenticatedCaller caller, Long poolId,
                                     ContactPoolContactBatchRequest request);
    Result<List<ContactVO>> claim(AuthenticatedCaller caller, Long poolId,
                                   ContactPoolContactBatchRequest request);
    Result<List<ContactVO>> assign(AuthenticatedCaller caller, Long poolId,
                                    ContactPoolAssignRequest request);
    Result<Void> deleteContacts(AuthenticatedCaller caller, Long poolId,
                                 ContactPoolContactBatchRequest request);
}
