package io.github.archetom.crm.infra.facade;

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
import io.github.archetom.crm.api.facade.CustomerPoolFacade;
import io.github.archetom.crm.application.service.CustomerPoolService;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerPoolFacadeImpl implements CustomerPoolFacade {

    private final CustomerPoolService service;
    private final FileFieldResponseEnricher fileFieldResponseEnricher;

    @Override
    public Result<Pager<CustomerPoolResponse>> query(
            AuthenticatedCaller caller, Integer page, Integer size) {
        return ResultUtil.map(service.query(caller, page, size), source -> {
            Pager<CustomerPoolResponse> target = PageUtil.copy(source);
            target.setObjectList(source.getObjectList().stream()
                    .map(CustomerPoolResponseAssembler::toResponse).toList());
            return target;
        });
    }

    @Override
    public Result<List<CustomerPoolResponse>> catalog(AuthenticatedCaller caller) {
        return ResultUtil.map(service.catalog(caller), values -> values.stream()
                .map(CustomerPoolResponseAssembler::toResponse).toList());
    }

    @Override
    public Result<CustomerPoolResponse> get(AuthenticatedCaller caller, Long poolId) {
        return ResultUtil.map(service.get(caller, poolId), CustomerPoolResponseAssembler::toResponse);
    }

    @Override
    public Result<CustomerPoolResponse> create(
            AuthenticatedCaller caller, CustomerPoolCreateRequest request) {
        return ResultUtil.map(service.create(caller, request), CustomerPoolResponseAssembler::toResponse);
    }

    @Override
    public Result<CustomerPoolResponse> update(
            AuthenticatedCaller caller, Long poolId, CustomerPoolUpdateRequest request) {
        return ResultUtil.map(service.update(caller, poolId, request),
                CustomerPoolResponseAssembler::toResponse);
    }

    @Override
    public Result<CustomerPoolResponse> updateStatus(
            AuthenticatedCaller caller, Long poolId, CustomerPoolStatusUpdateRequest request) {
        return ResultUtil.map(service.updateStatus(caller, poolId, request),
                CustomerPoolResponseAssembler::toResponse);
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long poolId, Long version) {
        return service.delete(caller, poolId, version);
    }

    @Override
    public Result<Void> transfer(
            AuthenticatedCaller caller, Long poolId, CustomerPoolTransferRequest request) {
        return service.transfer(caller, poolId, request);
    }

    @Override
    public Result<Pager<CustomerResponse>> customers(
            AuthenticatedCaller caller, Long poolId, CustomerPoolCustomerQueryRequest request) {
        return ResultUtil.map(service.customers(caller, poolId, request), source -> {
            Pager<CustomerResponse> target = PageUtil.copy(source);
            target.setObjectList(responses(caller, source.getObjectList()));
            return target;
        });
    }

    @Override
    public Result<CustomerResponse> customer(
            AuthenticatedCaller caller, Long poolId, Long customerId) {
        return ResultUtil.map(service.customer(caller, poolId, customerId),
                value -> response(caller, value));
    }

    @Override
    public Result<List<CustomerResponse>> release(
            AuthenticatedCaller caller, Long poolId, CustomerPoolCustomerBatchRequest request) {
        return customers(caller, service.release(caller, poolId, request));
    }

    @Override
    public Result<List<CustomerResponse>> claim(
            AuthenticatedCaller caller, Long poolId, CustomerPoolCustomerBatchRequest request) {
        return customers(caller, service.claim(caller, poolId, request));
    }

    @Override
    public Result<List<CustomerResponse>> assign(
            AuthenticatedCaller caller, Long poolId, CustomerPoolAssignRequest request) {
        return customers(caller, service.assign(caller, poolId, request));
    }

    @Override
    public Result<Void> deleteCustomers(
            AuthenticatedCaller caller, Long poolId, CustomerPoolCustomerBatchRequest request) {
        return service.deleteCustomers(caller, poolId, request);
    }

    private CustomerResponse response(AuthenticatedCaller caller, io.github.archetom.crm.application.vo.CustomerVO value) {
        return response(value, fileFieldResponseEnricher.crm(caller, "customer", value.id()));
    }

    private static CustomerResponse response(
            io.github.archetom.crm.application.vo.CustomerVO value, java.util.Map<String, java.util.List<io.github.archetom.crm.api.dto.response.AttachmentResponse>> fileFields) {
        return CustomerResponseAssembler.toResponse(value).setFileFields(fileFields);
    }

    /** One FILE-value read for the whole projection instead of one per row. */
    private java.util.List<CustomerResponse> responses(
            AuthenticatedCaller caller, java.util.List<io.github.archetom.crm.application.vo.CustomerVO> values) {
        var fileFields = fileFieldResponseEnricher.crm(
                caller, "customer", values.stream().map(value -> value.id()).toList());
        return values.stream()
                .map(value -> response(value, fileFields.getOrDefault(value.id(), java.util.Map.of())))
                .toList();
    }

    private Result<List<CustomerResponse>> customers(AuthenticatedCaller caller,
            Result<List<io.github.archetom.crm.application.vo.CustomerVO>> source) {
        return ResultUtil.map(source, values -> responses(caller, values));
    }
}
