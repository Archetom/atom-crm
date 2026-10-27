package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContractBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchCollaboratorAddRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchCollaboratorRemoveRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractCreateRequest;
import io.github.archetom.crm.api.dto.request.ContractOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractProductsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractQueryRequest;
import io.github.archetom.crm.api.dto.request.ContractUpdateRequest;
import io.github.archetom.crm.api.dto.response.ContractResponse;
import io.github.archetom.crm.api.dto.response.ContractCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.ContractCallerCollaboratorStateResponse;
import io.github.archetom.crm.api.dto.response.ContractListSummaryResponse;
import io.github.archetom.crm.api.dto.response.ContractProductSnapshotResponse;
import io.github.archetom.crm.api.dto.response.ContractProductsResponse;
import io.github.archetom.crm.api.facade.ContractFacade;
import io.github.archetom.crm.application.service.ContractCommandService;
import io.github.archetom.crm.application.service.ContractQueryService;
import io.github.archetom.crm.application.service.ContractProductService;
import io.github.archetom.crm.application.vo.ContractProductLineVO;
import io.github.archetom.crm.application.vo.ContractProductsVO;
import io.github.archetom.crm.application.vo.ContractVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContractFacadeImpl implements ContractFacade {
    private final ContractCommandService commandService;
    private final ContractQueryService queryService;
    private final ContractProductService productService;
    private final FileFieldResponseEnricher fileFieldResponseEnricher;

    @Override
    public Result<Pager<ContractResponse>> query(
            AuthenticatedCaller caller, ContractQueryRequest request) {
        return ResultUtil.map(queryService.query(caller, request), source -> page(caller, source));
    }

    @Override
    public Result<ContractListSummaryResponse> summary(
            AuthenticatedCaller caller, ContractQueryRequest request) {
        return ResultUtil.map(queryService.summary(caller, request), value ->
                new ContractListSummaryResponse().setCount(value.count())
                        .setTotalAmount(value.totalAmount())
                        .setReceivedAmount(value.receivedAmount())
                        .setOutstandingAmount(value.outstandingAmount()));
    }

    @Override
    public Result<ContractResponse> get(AuthenticatedCaller caller, Long contractId) {
        return ResultUtil.map(queryService.get(caller, contractId),
                value -> response(caller, value));
    }

    @Override
    public Result<ContractCollaboratorBaselineResponse> collaborators(
            AuthenticatedCaller caller, Long contractId) {
        return ResultUtil.map(queryService.collaborators(caller, contractId), value ->
                new ContractCollaboratorBaselineResponse().setVersion(value.version())
                        .setCollaborators(value.collaborators().stream().map(collaborator ->
                                new io.github.archetom.crm.api.dto.response.ContractCollaboratorResponse()
                                        .setActorId(collaborator.actorId())
                                        .setAccess(collaborator.access())
                                        .setEffectiveUntil(collaborator.effectiveUntil())).toList())
                        .setCallerState(ContractCallerCollaboratorStateResponse
                                .valueOf(value.callerState())));
    }

    @Override
    public Result<ContractProductsResponse> products(
            AuthenticatedCaller caller, Long contractId) {
        return ResultUtil.map(productService.get(caller, contractId), this::productsResponse);
    }

    @Override
    public Result<ContractResponse> create(
            AuthenticatedCaller caller, ContractCreateRequest request) {
        return ResultUtil.map(commandService.create(caller, request),
                value -> response(caller, value));
    }

    @Override
    public Result<ContractResponse> update(
            AuthenticatedCaller caller, Long contractId, ContractUpdateRequest request) {
        return ResultUtil.map(commandService.update(caller, contractId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<ContractProductsResponse> replaceProducts(
            AuthenticatedCaller caller, Long contractId,
            ContractProductsUpdateRequest request) {
        return ResultUtil.map(productService.replace(caller, contractId, request),
                this::productsResponse);
    }

    @Override
    public Result<ContractResponse> updateOwner(
            AuthenticatedCaller caller, Long contractId,
            ContractOwnerUpdateRequest request) {
        return ResultUtil.map(commandService.updateOwner(caller, contractId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> updateOwners(
            AuthenticatedCaller caller, ContractBatchOwnerUpdateRequest request) {
        return commandService.updateOwners(caller, request);
    }

    @Override
    public Result<Void> addCollaborators(
            AuthenticatedCaller caller, ContractBatchCollaboratorAddRequest request) {
        return commandService.addCollaborators(caller, request);
    }

    @Override
    public Result<Void> removeCollaborators(
            AuthenticatedCaller caller, ContractBatchCollaboratorRemoveRequest request) {
        return commandService.removeCollaborators(caller, request);
    }

    @Override
    public Result<ContractResponse> replaceCollaborators(
            AuthenticatedCaller caller, Long contractId,
            ContractCollaboratorsUpdateRequest request) {
        return ResultUtil.map(commandService.replaceCollaborators(caller, contractId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<ContractResponse> exitCollaborator(
            AuthenticatedCaller caller, Long contractId, Long version) {
        return ResultUtil.map(commandService.exitCollaborator(caller, contractId, version),
                value -> response(caller, value));
    }

    @Override
    public Result<ContractResponse> voidContract(
            AuthenticatedCaller caller, Long contractId, Long version) {
        return ResultUtil.map(commandService.voidContract(caller, contractId, version),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long contractId, Long version) {
        return commandService.delete(caller, contractId, version);
    }

    @Override
    public Result<Void> deleteBatch(
            AuthenticatedCaller caller, ContractBatchDeleteRequest request) {
        return commandService.deleteBatch(caller, request);
    }

    private ContractResponse response(AuthenticatedCaller caller, ContractVO value) {
        return response(value, fileFieldResponseEnricher.crm(caller, "contract", value.id()));
    }

    private static ContractResponse response(
            ContractVO value, java.util.Map<String, java.util.List<io.github.archetom.crm.api.dto.response.AttachmentResponse>> fileFields) {
        return ContractResponseAssembler.toResponse(value).setFileFields(fileFields);
    }

    /** One FILE-value read for the whole projection instead of one per row. */
    private java.util.List<ContractResponse> responses(
            AuthenticatedCaller caller, java.util.List<ContractVO> values) {
        var fileFields = fileFieldResponseEnricher.crm(
                caller, "contract", values.stream().map(value -> value.id()).toList());
        return values.stream()
                .map(value -> response(value, fileFields.getOrDefault(value.id(), java.util.Map.of())))
                .toList();
    }

    private ContractProductsResponse productsResponse(ContractProductsVO value) {
        return new ContractProductsResponse().setContractId(value.contractId())
                .setVersion(value.version()).setLines(value.lines().stream()
                        .map(this::productSnapshotResponse).toList());
    }

    private ContractProductSnapshotResponse productSnapshotResponse(ContractProductLineVO value) {
        return new ContractProductSnapshotResponse().setProductId(value.productId())
                .setProductName(value.productName()).setProductCode(value.productCode())
                .setCategoryName(value.categoryName()).setUnit(value.unit())
                .setQuantity(value.quantity()).setUnitPrice(value.unitPrice());
    }

    private Pager<ContractResponse> page(AuthenticatedCaller caller, Pager<ContractVO> source) {
        Pager<ContractResponse> target = PageUtil.copy(source);
        target.setMeta(null);
        target.setObjectList(responses(caller, source.getObjectList()));
        return target;
    }
}
