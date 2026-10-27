package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OpportunityBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.OpportunityBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityContactsUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityOutcomeUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPrimaryContactUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityQueryRequest;
import io.github.archetom.crm.api.dto.request.OpportunityStageUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityProductsUpdateRequest;
import io.github.archetom.crm.api.dto.response.OpportunityContactResponse;
import io.github.archetom.crm.api.dto.response.OpportunityCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.OpportunityContactsBaselineResponse;
import io.github.archetom.crm.api.dto.response.OpportunityEditableBaselineResponse;
import io.github.archetom.crm.api.dto.response.OpportunityResponse;
import io.github.archetom.crm.api.dto.response.OpportunitySummaryResponse;
import io.github.archetom.crm.api.dto.response.OpportunityProductLineResponse;
import io.github.archetom.crm.api.dto.response.OpportunityProductsResponse;
import io.github.archetom.crm.api.facade.OpportunityFacade;
import io.github.archetom.crm.application.service.OpportunityCommandService;
import io.github.archetom.crm.application.service.OpportunityQueryService;
import io.github.archetom.crm.application.service.OpportunityProductService;
import io.github.archetom.crm.application.vo.OpportunityContactVO;
import io.github.archetom.crm.application.vo.OpportunityVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpportunityFacadeImpl implements OpportunityFacade {

    private final OpportunityCommandService commandService;
    private final OpportunityQueryService queryService;
    private final OpportunityProductService productService;
    private final FileFieldResponseEnricher fileFieldResponseEnricher;

    @Override
    public Result<Pager<OpportunityResponse>> query(
            AuthenticatedCaller caller, OpportunityQueryRequest request) {
        return ResultUtil.map(queryService.query(caller, request), source -> page(caller, source));
    }

    @Override
    public Result<OpportunityResponse> get(
            AuthenticatedCaller caller, Long opportunityId) {
        return ResultUtil.map(queryService.get(caller, opportunityId),
                value -> response(caller, value));
    }

    @Override
    public Result<OpportunityEditableBaselineResponse> editableBaseline(
            AuthenticatedCaller caller, Long opportunityId) {
        return ResultUtil.map(queryService.editableBaseline(caller, opportunityId),
                OpportunityResponseAssembler::toResponse);
    }

    @Override
    public Result<OpportunityCollaboratorBaselineResponse> collaborators(
            AuthenticatedCaller caller, Long opportunityId) {
        return ResultUtil.map(queryService.collaborators(caller, opportunityId),
                OpportunityResponseAssembler::toResponse);
    }

    @Override
    public Result<OpportunityContactsBaselineResponse> contactsBaseline(
            AuthenticatedCaller caller, Long opportunityId) {
        return ResultUtil.map(queryService.contactsBaseline(caller, opportunityId),
                OpportunityResponseAssembler::toResponse);
    }

    @Override
    public Result<OpportunityResponse> create(
            AuthenticatedCaller caller, OpportunityCreateRequest request) {
        return ResultUtil.map(commandService.create(caller, request),
                value -> response(caller, value));
    }

    @Override
    public Result<OpportunityResponse> update(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityUpdateRequest request) {
        return ResultUtil.map(commandService.update(caller, opportunityId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long opportunityId, Long version) {
        return commandService.delete(caller, opportunityId, version);
    }

    @Override
    public Result<Void> deleteBatch(
            AuthenticatedCaller caller, OpportunityBatchDeleteRequest request) {
        return commandService.deleteBatch(caller, request);
    }

    @Override
    public Result<OpportunityResponse> updateOwner(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityOwnerUpdateRequest request) {
        return ResultUtil.map(commandService.updateOwner(caller, opportunityId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> updateOwners(
            AuthenticatedCaller caller, OpportunityBatchOwnerUpdateRequest request) {
        return commandService.updateOwners(caller, request);
    }

    @Override
    public Result<OpportunityResponse> replaceCollaborators(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityCollaboratorsUpdateRequest request) {
        return ResultUtil.map(commandService.replaceCollaborators(
                caller, opportunityId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> exitCollaborator(
            AuthenticatedCaller caller, Long opportunityId, Long version) {
        return commandService.exitCollaborator(caller, opportunityId, version);
    }

    @Override
    public Result<OpportunityResponse> replaceContacts(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityContactsUpdateRequest request) {
        return ResultUtil.map(commandService.replaceContacts(
                caller, opportunityId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<OpportunityResponse> updatePrimaryContact(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityPrimaryContactUpdateRequest request) {
        return ResultUtil.map(commandService.updatePrimaryContact(
                caller, opportunityId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<OpportunityResponse> updateStage(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityStageUpdateRequest request) {
        return ResultUtil.map(commandService.updateStage(caller, opportunityId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<OpportunityResponse> updateOutcome(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityOutcomeUpdateRequest request) {
        return ResultUtil.map(commandService.updateOutcome(caller, opportunityId, request),
                value -> response(caller, value));
    }

    @Override
    public Result<Void> setStarred(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityStarUpdateRequest request) {
        return commandService.setStarred(caller, opportunityId, request);
    }

    @Override
    public Result<Pager<OpportunityContactResponse>> contacts(
            AuthenticatedCaller caller, Long opportunityId,
            Integer page, Integer size) {
        return ResultUtil.map(queryService.contacts(
                caller, opportunityId, page, size), this::contactPage);
    }

    @Override
    public Result<OpportunitySummaryResponse> summary(
            AuthenticatedCaller caller, OpportunityQueryRequest request) {
        return ResultUtil.map(queryService.summary(caller, request), value ->
                new OpportunitySummaryResponse().setCount(value.count())
                        .setEstimatedAmount(value.estimatedAmount()));
    }

    @Override
    public Result<OpportunityProductsResponse> products(
            AuthenticatedCaller caller, Long opportunityId) {
        return ResultUtil.map(productService.get(caller, opportunityId), value ->
                new OpportunityProductsResponse().setOpportunityId(value.opportunityId())
                        .setLines(value.lines().stream().map(line ->
                                new OpportunityProductLineResponse()
                                        .setProductId(line.productId())
                                        .setProductName(line.productName())
                                        .setProductCode(line.productCode())
                                        .setCategoryName(line.categoryName())
                                        .setUnit(line.unit()).setListPrice(line.listPrice())
                                        .setUnitPrice(line.unitPrice())
                                        .setQuantity(line.quantity())
                                        .setDiscountRate(line.discountRate())
                                        .setLineAmount(line.lineAmount())
                                        .setDisplayOrder(line.displayOrder())).toList())
                        .setTotalAmount(value.totalAmount()).setVersion(value.version()));
    }

    @Override
    public Result<OpportunityProductsResponse> replaceProducts(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityProductsUpdateRequest request) {
        return ResultUtil.map(productService.replace(caller, opportunityId, request), value ->
                new OpportunityProductsResponse().setOpportunityId(value.opportunityId())
                        .setLines(value.lines().stream().map(line ->
                                new OpportunityProductLineResponse()
                                        .setProductId(line.productId())
                                        .setProductName(line.productName())
                                        .setProductCode(line.productCode())
                                        .setCategoryName(line.categoryName())
                                        .setUnit(line.unit()).setListPrice(line.listPrice())
                                        .setUnitPrice(line.unitPrice())
                                        .setQuantity(line.quantity())
                                        .setDiscountRate(line.discountRate())
                                        .setLineAmount(line.lineAmount())
                                        .setDisplayOrder(line.displayOrder())).toList())
                        .setTotalAmount(value.totalAmount()).setVersion(value.version()));
    }

    private OpportunityResponse response(AuthenticatedCaller caller, OpportunityVO value) {
        return response(value, fileFieldResponseEnricher.crm(caller, "opportunity", value.id()));
    }

    private static OpportunityResponse response(
            OpportunityVO value, java.util.Map<String, java.util.List<io.github.archetom.crm.api.dto.response.AttachmentResponse>> fileFields) {
        return OpportunityResponseAssembler.toResponse(value).setFileFields(fileFields);
    }

    /** One FILE-value read for the whole projection instead of one per row. */
    private java.util.List<OpportunityResponse> responses(
            AuthenticatedCaller caller, java.util.List<OpportunityVO> values) {
        var fileFields = fileFieldResponseEnricher.crm(
                caller, "opportunity", values.stream().map(value -> value.id()).toList());
        return values.stream()
                .map(value -> response(value, fileFields.getOrDefault(value.id(), java.util.Map.of())))
                .toList();
    }

    private Pager<OpportunityResponse> page(AuthenticatedCaller caller, Pager<OpportunityVO> source) {
        Pager<OpportunityResponse> target = PageUtil.copy(source);
        target.setMeta(null);
        target.setObjectList(responses(caller, source.getObjectList()));
        return target;
    }

    private Pager<OpportunityContactResponse> contactPage(
            Pager<OpportunityContactVO> source) {
        Pager<OpportunityContactResponse> target = PageUtil.copy(source);
        target.setMeta(null);
        target.setObjectList(source.getObjectList().stream()
                .map(OpportunityResponseAssembler::toResponse).toList());
        return target;
    }
}
