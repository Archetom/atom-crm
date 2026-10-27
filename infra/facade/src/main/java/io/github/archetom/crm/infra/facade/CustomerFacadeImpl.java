package io.github.archetom.crm.infra.facade;

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
import io.github.archetom.crm.api.facade.CustomerFacade;
import io.github.archetom.crm.application.service.CustomerCommandService;
import io.github.archetom.crm.application.service.CustomerQueryService;
import io.github.archetom.crm.application.vo.CustomerVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerFacadeImpl implements CustomerFacade {

    private final CustomerCommandService commandService;
    private final CustomerQueryService queryService;
    private final FileFieldResponseEnricher fileFieldResponseEnricher;

    @Override
    public Result<Pager<CustomerResponse>> query(
            AuthenticatedCaller caller, CustomerQueryRequest request) {
        return ResultUtil.map(queryService.query(caller, request), source -> page(caller, source));
    }

    @Override
    public Result<CustomerResponse> get(AuthenticatedCaller caller, Long customerId) {
        return ResultUtil.map(queryService.get(caller, customerId), value -> response(caller, value));
    }

    @Override
    public Result<CustomerResponse> create(
            AuthenticatedCaller caller, CustomerCreateRequest request) {
        return ResultUtil.map(commandService.create(caller, request), value -> response(caller, value));
    }

    @Override
    public Result<CustomerResponse> update(
            AuthenticatedCaller caller, Long customerId, CustomerUpdateRequest request) {
        return ResultUtil.map(commandService.update(caller, customerId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long customerId, Long version) {
        return commandService.delete(caller, customerId, version);
    }

    @Override
    public Result<CustomerResponse> updateState(
            AuthenticatedCaller caller, Long customerId, CustomerStateUpdateRequest request) {
        return ResultUtil.map(commandService.updateState(caller, customerId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<CustomerResponse> updateOwner(
            AuthenticatedCaller caller, Long customerId, CustomerOwnerUpdateRequest request) {
        return ResultUtil.map(commandService.updateOwner(caller, customerId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<CustomerResponse> replaceTags(
            AuthenticatedCaller caller, Long customerId, CustomerTagsUpdateRequest request) {
        return ResultUtil.map(commandService.replaceTags(caller, customerId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<List<CustomerResponse>> changeTags(
            AuthenticatedCaller caller, CustomerBatchTagRequest request) {
        return ResultUtil.map(commandService.changeTags(caller, request), values ->
                responses(caller, values));
    }

    @Override
    public Result<CustomerCollaboratorBaselineResponse> collaborators(
            AuthenticatedCaller caller, Long customerId) {
        return ResultUtil.map(queryService.collaborators(caller, customerId),
                CustomerResponseAssembler::toResponse);
    }

    @Override
    public Result<CustomerResponse> replaceCollaborators(
            AuthenticatedCaller caller, Long customerId,
            CustomerCollaboratorsUpdateRequest request) {
        return ResultUtil.map(commandService.replaceCollaborators(caller, customerId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> exitCollaborator(
            AuthenticatedCaller caller, Long customerId, Long version) {
        return commandService.exitCollaborator(caller, customerId, version);
    }

    @Override
    public Result<CustomerResponse> updateParent(
            AuthenticatedCaller caller, Long customerId, CustomerParentUpdateRequest request) {
        return ResultUtil.map(commandService.updateParent(caller, customerId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<CustomerHierarchyResponse> hierarchy(
            AuthenticatedCaller caller, Long customerId) {
        return ResultUtil.map(queryService.hierarchy(caller, customerId), value ->
                new CustomerHierarchyResponse().setRequestedCustomerId(value.requestedCustomerId())
                        .setCustomers(responses(caller, value.customers())));
    }

    @Override
    public Result<Void> setStarred(
            AuthenticatedCaller caller, Long customerId, CustomerStarUpdateRequest request) {
        return commandService.setStarred(caller, customerId, request);
    }

    @Override
    public Result<CustomerDuplicateResponse> duplicateCheck(
            AuthenticatedCaller caller, CustomerDuplicateCheckRequest request) {
        return ResultUtil.map(queryService.duplicateCheck(caller, request), value ->
                new CustomerDuplicateResponse().setDuplicate(value));
    }

    @Override
    public Result<Pager<NearbyCustomerResponse>> nearby(
            AuthenticatedCaller caller, BigDecimal longitude, BigDecimal latitude,
            Integer radiusMeters, Integer page, Integer size) {
        return ResultUtil.map(queryService.nearby(caller, longitude, latitude,
                radiusMeters, page, size), source -> nearbyPage(caller, source));
    }

    @Override
    public Result<List<CustomerResponse>> summaries(
            AuthenticatedCaller caller, List<Long> ids) {
        return ResultUtil.map(queryService.summaries(caller, ids), values ->
                responses(caller, values));
    }

    @Override
    public Result<List<String>> tags(AuthenticatedCaller caller) {
        return queryService.tags(caller);
    }

    private CustomerResponse response(AuthenticatedCaller caller, CustomerVO value) {
        return response(value, fileFieldResponseEnricher.crm(caller, "customer", value.id()));
    }

    private static CustomerResponse response(
            CustomerVO value, java.util.Map<String, java.util.List<
                    io.github.archetom.crm.api.dto.response.AttachmentResponse>> fileFields) {
        return CustomerResponseAssembler.toResponse(value).setFileFields(fileFields);
    }

    /** One FILE-value read for the whole projection instead of one per row. */
    private List<CustomerResponse> responses(AuthenticatedCaller caller, List<CustomerVO> values) {
        var fileFields = fileFieldResponseEnricher.crm(
                caller, "customer", values.stream().map(CustomerVO::id).toList());
        return values.stream()
                .map(value -> response(value, fileFields.getOrDefault(value.id(), java.util.Map.of())))
                .toList();
    }

    private Pager<CustomerResponse> page(AuthenticatedCaller caller, Pager<CustomerVO> source) {
        Pager<CustomerResponse> target = PageUtil.copy(source);
        target.setMeta(null);
        target.setObjectList(responses(caller, source.getObjectList()));
        return target;
    }

    private Pager<NearbyCustomerResponse> nearbyPage(AuthenticatedCaller caller,
            Pager<io.github.archetom.crm.application.vo.NearbyCustomerVO> source) {
        Pager<NearbyCustomerResponse> target = PageUtil.copy(source);
        target.setMeta(null);
        List<CustomerResponse> customers = responses(caller, source.getObjectList().stream()
                .map(io.github.archetom.crm.application.vo.NearbyCustomerVO::customer).toList());
        List<NearbyCustomerResponse> rows = new java.util.ArrayList<>();
        for (int index = 0; index < source.getObjectList().size(); index++) {
            rows.add(new NearbyCustomerResponse()
                    .setCustomer(customers.get(index))
                    .setDistanceMeters(source.getObjectList().get(index).distanceMeters()));
        }
        target.setObjectList(List.copyOf(rows));
        return target;
    }
}
