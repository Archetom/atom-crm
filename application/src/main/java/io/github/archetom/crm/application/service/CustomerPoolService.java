package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerPoolAssignRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolCreateRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolCustomerBatchRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolCustomerQueryRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolTransferRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolUpdateRequest;
import io.github.archetom.crm.application.vo.CustomerPoolVO;
import io.github.archetom.crm.application.vo.CustomerVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface CustomerPoolService {
    Result<Pager<CustomerPoolVO>> query(AuthenticatedCaller caller, Integer page, Integer size);
    Result<List<CustomerPoolVO>> catalog(AuthenticatedCaller caller);
    Result<CustomerPoolVO> get(AuthenticatedCaller caller, Long poolId);
    Result<CustomerPoolVO> create(AuthenticatedCaller caller, CustomerPoolCreateRequest request);
    Result<CustomerPoolVO> update(AuthenticatedCaller caller, Long poolId, CustomerPoolUpdateRequest request);
    Result<CustomerPoolVO> updateStatus(AuthenticatedCaller caller, Long poolId,
                                        CustomerPoolStatusUpdateRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long poolId, Long version);
    Result<Void> transfer(AuthenticatedCaller caller, Long poolId, CustomerPoolTransferRequest request);
    Result<Pager<CustomerVO>> customers(AuthenticatedCaller caller, Long poolId,
                                        CustomerPoolCustomerQueryRequest request);
    Result<CustomerVO> customer(AuthenticatedCaller caller, Long poolId, Long customerId);
    Result<List<CustomerVO>> release(AuthenticatedCaller caller, Long poolId,
                                     CustomerPoolCustomerBatchRequest request);
    Result<List<CustomerVO>> claim(AuthenticatedCaller caller, Long poolId,
                                   CustomerPoolCustomerBatchRequest request);
    Result<List<CustomerVO>> assign(AuthenticatedCaller caller, Long poolId,
                                    CustomerPoolAssignRequest request);
    Result<Void> deleteCustomers(AuthenticatedCaller caller, Long poolId,
                                 CustomerPoolCustomerBatchRequest request);
}
