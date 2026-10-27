package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReportRankingRequest;
import io.github.archetom.crm.api.dto.request.ReportTrendRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionCreateRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionQueryRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionRunRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionUpdateRequest;
import io.github.archetom.crm.api.dto.response.CoreReportResponse;
import io.github.archetom.crm.api.dto.response.ReportRankingResponse;
import io.github.archetom.crm.api.dto.response.ReportTrendResponse;
import io.github.archetom.crm.api.dto.response.FieldOptionResponse;
import io.github.archetom.crm.api.dto.response.FilterConditionResponse;
import io.github.archetom.crm.api.dto.response.ReportCatalogResponse;
import io.github.archetom.crm.api.dto.response.ReportDefinitionResponse;
import io.github.archetom.crm.api.dto.response.ReportDefinitionRunResponse;
import io.github.archetom.crm.api.dto.response.ReportDefinitionSummaryResponse;
import io.github.archetom.crm.api.facade.ReportFacade;
import io.github.archetom.crm.application.service.ReportQueryService;
import io.github.archetom.crm.application.service.ReportDefinitionService;
import io.github.archetom.crm.application.vo.ReportCatalogVO;
import io.github.archetom.crm.application.vo.ReportDefinitionRunVO;
import io.github.archetom.crm.application.vo.ReportDefinitionSummaryVO;
import io.github.archetom.crm.application.vo.ReportDefinitionVO;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.crm.application.vo.ReportRankingVO;
import io.github.archetom.crm.application.vo.ReportTrendVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import io.github.archetom.common.result.Pager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportFacadeImpl implements ReportFacade {

    private final ReportQueryService queryService;
    private final ReportDefinitionService definitionService;

    @Override
    public Result<CoreReportResponse> core(AuthenticatedCaller caller) {
        return ResultUtil.map(queryService.core(caller), value -> new CoreReportResponse()
                .setOpenOpportunityCount(value.openOpportunityCount())
                .setOpenOpportunityEstimatedAmount(value.openOpportunityEstimatedAmount())
                .setWonOpportunityCount(value.wonOpportunityCount())
                .setWonOpportunityEstimatedAmount(value.wonOpportunityEstimatedAmount())
                .setLostOpportunityCount(value.lostOpportunityCount())
                .setLostOpportunityEstimatedAmount(value.lostOpportunityEstimatedAmount())
                .setActiveContractCount(value.activeContractCount())
                .setActiveContractAmount(value.activeContractAmount())
                .setReceivableCount(value.receivableCount())
                .setReceivableAmount(value.receivableAmount()));
    }

    @Override
    public Result<ReportTrendResponse> trend(
            AuthenticatedCaller caller, ReportTrendRequest request) {
        return ResultUtil.map(queryService.trend(caller, request), value ->
                new ReportTrendResponse()
                        .setMetric(value.metric())
                        .setFrom(value.from())
                        .setTo(value.to())
                        .setGranularity(value.granularity())
                        .setPoints(value.points().stream()
                                .map(ReportFacadeImpl::trendPoint).toList()));
    }

    @Override
    public Result<ReportRankingResponse> ranking(
            AuthenticatedCaller caller, ReportRankingRequest request) {
        return ResultUtil.map(queryService.ranking(caller, request), value ->
                new ReportRankingResponse()
                        .setMetric(value.metric())
                        .setFrom(value.from())
                        .setTo(value.to())
                        .setItems(value.items().stream()
                                .map(ReportFacadeImpl::rankingItem).toList()));
    }

    @Override
    public Result<ReportCatalogResponse> catalog(
            AuthenticatedCaller caller, String objectType) {
        return ResultUtil.map(definitionService.catalog(caller, objectType), this::catalogResponse);
    }

    @Override
    public Result<Pager<ReportDefinitionSummaryResponse>> queryDefinitions(
            AuthenticatedCaller caller, ReportDefinitionQueryRequest request) {
        return ResultUtil.map(definitionService.query(caller, request), source -> {
            Pager<ReportDefinitionSummaryResponse> target = PageUtil.copy(source);
            target.setMeta(null);
            target.setObjectList(source.getObjectList().stream()
                    .map(this::summaryResponse).toList());
            return target;
        });
    }

    @Override
    public Result<ReportDefinitionResponse> getDefinition(
            AuthenticatedCaller caller, Long definitionId) {
        return ResultUtil.map(definitionService.get(caller, definitionId), this::definitionResponse);
    }

    @Override
    public Result<ReportDefinitionResponse> createDefinition(
            AuthenticatedCaller caller, ReportDefinitionCreateRequest request) {
        return ResultUtil.map(definitionService.create(caller, request), this::definitionResponse);
    }

    @Override
    public Result<ReportDefinitionResponse> updateDefinition(
            AuthenticatedCaller caller, Long definitionId,
            ReportDefinitionUpdateRequest request) {
        return ResultUtil.map(definitionService.update(caller, definitionId, request),
                this::definitionResponse);
    }

    @Override
    public Result<Void> deleteDefinition(
            AuthenticatedCaller caller, Long definitionId, Long version) {
        return definitionService.delete(caller, definitionId, version);
    }

    @Override
    public Result<ReportDefinitionRunResponse> runDefinition(
            AuthenticatedCaller caller, Long definitionId,
            ReportDefinitionRunRequest request) {
        return ResultUtil.map(definitionService.run(caller, definitionId, request),
                this::runResponse);
    }

    private ReportCatalogResponse catalogResponse(ReportCatalogVO value) {
        return new ReportCatalogResponse().setObjectType(value.objectType())
                .setSupportedObjectTypes(value.supportedObjectTypes())
                .setTimeFields(value.timeFields().stream().map(this::catalogField).toList())
                .setDimensionFields(value.dimensionFields().stream().map(this::catalogField).toList())
                .setSumFields(value.sumFields().stream().map(this::catalogField).toList())
                .setFilterFields(value.filterFields().stream().map(this::catalogField).toList())
                .setMeasureTypes(value.measureTypes());
    }

    private ReportCatalogResponse.ReportCatalogFieldResponse catalogField(
            ReportCatalogVO.ReportCatalogFieldVO value) {
        return new ReportCatalogResponse.ReportCatalogFieldResponse()
                .setFieldCode(value.fieldCode()).setDisplayName(value.displayName())
                .setFieldType(value.fieldType()).setGranularities(value.granularities())
                .setOperators(value.operators()).setOptions(value.options().stream()
                        .map(option -> new FieldOptionResponse().setValue(option.value())
                                .setLabel(option.label()).setOrder(option.order())).toList());
    }

    private ReportDefinitionSummaryResponse summaryResponse(ReportDefinitionSummaryVO value) {
        return new ReportDefinitionSummaryResponse().setId(value.id()).setName(value.name())
                .setDescription(value.description()).setObjectType(value.objectType())
                .setTimeFieldCode(value.timeFieldCode()).setGranularity(value.granularity())
                .setDimensionFieldCode(value.dimensionFieldCode())
                .setMeasureCount(value.measureCount()).setFilterCount(value.filterCount())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }

    private ReportDefinitionResponse definitionResponse(ReportDefinitionVO value) {
        return new ReportDefinitionResponse().setId(value.id()).setName(value.name())
                .setDescription(value.description()).setObjectType(value.objectType())
                .setTimeFieldCode(value.timeFieldCode()).setGranularity(value.granularity())
                .setDimensionFieldCode(value.dimensionFieldCode())
                .setFilters(value.filters().stream().map(filter -> new FilterConditionResponse()
                        .setFieldCode(filter.fieldCode()).setOperator(filter.operator())
                        .setValues(filter.values())).toList())
                .setMeasures(value.measures().stream().map(measure ->
                        new ReportDefinitionResponse.ReportMeasureResponse()
                                .setType(measure.type()).setFieldCode(measure.fieldCode())
                                .setKey(measure.key())).toList())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }

    private ReportDefinitionRunResponse runResponse(ReportDefinitionRunVO value) {
        return new ReportDefinitionRunResponse().setDefinitionId(value.definitionId())
                .setDefinitionVersion(value.definitionVersion()).setObjectType(value.objectType())
                .setFrom(value.from()).setTo(value.to()).setGranularity(value.granularity())
                .setColumns(value.columns().stream().map(column ->
                        new ReportDefinitionRunResponse.ReportColumnResponse()
                                .setKey(column.key()).setKind(column.kind())
                                .setFieldCode(column.fieldCode()).setFieldType(column.fieldType())
                                .setLabel(column.label())).toList())
                .setRows(value.rows().stream().map(row ->
                        new ReportDefinitionRunResponse.ReportRowResponse()
                                .setPeriodStart(row.periodStart())
                                .setDimension(row.dimension() == null ? null
                                        : new ReportDefinitionRunResponse.ReportDimensionResponse()
                                        .setKey(row.dimension().key())
                                        .setLabel(row.dimension().label()))
                                .setValues(row.values())).toList());
    }

    private static ReportTrendResponse.ReportTrendPointResponse trendPoint(
            ReportTrendVO.ReportTrendPointVO value) {
        return new ReportTrendResponse.ReportTrendPointResponse()
                .setPeriodStart(value.periodStart())
                .setCount(value.count())
                .setAmount(value.amount());
    }

    private static ReportRankingResponse.ReportRankingItemResponse rankingItem(
            ReportRankingVO.ReportRankingItemVO value) {
        return new ReportRankingResponse.ReportRankingItemResponse()
                .setRank(value.rank())
                .setOwnerActorId(value.ownerActorId())
                .setCount(value.count())
                .setAmount(value.amount());
    }
}
