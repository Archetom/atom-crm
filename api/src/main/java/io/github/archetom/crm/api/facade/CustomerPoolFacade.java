package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerPoolAssignRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolCreateRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolCustomerBatchRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolCustomerQueryRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolTransferRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolUpdateRequest;
import io.github.archetom.crm.api.dto.response.CustomerPoolResponse;
import io.github.archetom.crm.api.dto.response.CustomerResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface CustomerPoolFacade {
    Result<Pager<CustomerPoolResponse>> query(AuthenticatedCaller caller, Integer page, Integer size);
    Result<List<CustomerPoolResponse>> catalog(AuthenticatedCaller caller);
    Result<CustomerPoolResponse> get(AuthenticatedCaller caller, Long poolId);
    Result<CustomerPoolResponse> create(AuthenticatedCaller caller, CustomerPoolCreateRequest request);
    Result<CustomerPoolResponse> update(AuthenticatedCaller caller, Long poolId, CustomerPoolUpdateRequest request);
    Result<CustomerPoolResponse> updateStatus(AuthenticatedCaller caller, Long poolId,
                                              CustomerPoolStatusUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long poolId, Long version);
    Result<Void> transfer(AuthenticatedCaller caller, Long poolId, CustomerPoolTransferRequest request);
    Result<Pager<CustomerResponse>> customers(AuthenticatedCaller caller, Long poolId,
                                              CustomerPoolCustomerQueryRequest request);
    Result<CustomerResponse> customer(AuthenticatedCaller caller, Long poolId, Long customerId);
    Result<List<CustomerResponse>> release(AuthenticatedCaller caller, Long poolId,
                                           CustomerPoolCustomerBatchRequest request);
    Result<List<CustomerResponse>> claim(AuthenticatedCaller caller, Long poolId,
                                         CustomerPoolCustomerBatchRequest request);
    Result<List<CustomerResponse>> assign(AuthenticatedCaller caller, Long poolId,
                                          CustomerPoolAssignRequest request);
    Result<Void> deleteCustomers(AuthenticatedCaller caller, Long poolId,
                                 CustomerPoolCustomerBatchRequest request);
}
