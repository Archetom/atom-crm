package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.response.DashboardOverviewResponse;
import io.github.archetom.crm.api.facade.DashboardOverviewFacade;
import io.github.archetom.crm.application.service.DashboardOverviewService;
import io.github.archetom.crm.application.vo.DashboardOverviewVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Maps the application-owned dashboard projection to the HTTP facade contract. */
@Service
@RequiredArgsConstructor
public class DashboardOverviewFacadeImpl implements DashboardOverviewFacade {

    private final DashboardOverviewService service;

    @Override
    public Result<DashboardOverviewResponse> get(AuthenticatedCaller caller) {
        return ResultUtil.map(service.get(caller), value -> new DashboardOverviewResponse()
                .setAsOfDate(value.asOfDate())
                .setTimeRange(value.timeRange())
                .setFrom(value.from())
                .setTo(value.to())
                .setPreferredDataScope(value.preferredDataScope())
                .setGranularity(value.granularity())
                .setCards(value.cards().stream().map(this::card).toList())
                .setSalesBrief(value.salesBrief().stream().map(this::salesBrief).toList())
                .setDataSummary(value.dataSummary().stream().map(this::dataSummary).toList())
                .setSalesFunnel(salesFunnel(value.salesFunnel()))
                .setCustomerContactReminder(customerContactReminder(value.customerContactReminder()))
                .setRankings(value.rankings().stream().map(this::ranking).toList())
                .setTarget(target(value.target())));
    }

    private DashboardOverviewResponse.CardResponse card(DashboardOverviewVO.CardVO value) {
        return new DashboardOverviewResponse.CardResponse()
                .setMetric(value.metric())
                .setStatus(value.status())
                .setCount(value.count())
                .setAmount(value.amount())
                .setPoints(value.points().stream().map(this::point).toList());
    }

    private DashboardOverviewResponse.PointResponse point(DashboardOverviewVO.PointVO value) {
        return new DashboardOverviewResponse.PointResponse()
                .setPeriodStart(value.periodStart())
                .setCount(value.count())
                .setAmount(value.amount());
    }

    private DashboardOverviewResponse.SalesBriefResponse salesBrief(
            DashboardOverviewVO.SalesBriefVO value) {
        return new DashboardOverviewResponse.SalesBriefResponse()
                .setMetric(value.metric())
                .setStatus(value.status())
                .setUnit(value.unit())
                .setValue(value.value())
                .setPreviousValue(value.previousValue())
                .setComparisonRate(value.comparisonRate());
    }

    private DashboardOverviewResponse.DataSummaryResponse dataSummary(
            DashboardOverviewVO.DataSummaryVO value) {
        return new DashboardOverviewResponse.DataSummaryResponse()
                .setMetric(value.metric())
                .setStatus(value.status())
                .setUnit(value.unit())
                .setValue(value.value());
    }

    private DashboardOverviewResponse.RankingResponse ranking(DashboardOverviewVO.RankingVO value) {
        return new DashboardOverviewResponse.RankingResponse()
                .setMetric(value.metric())
                .setStatus(value.status())
                .setItems(value.items().stream().map(this::rankingItem).toList());
    }

    private DashboardOverviewResponse.SalesFunnelResponse salesFunnel(
            DashboardOverviewVO.SalesFunnelVO value) {
        return new DashboardOverviewResponse.SalesFunnelResponse()
                .setStatus(value.status())
                .setPipelines(value.pipelines().stream().map(pipeline ->
                        new DashboardOverviewResponse.SalesFunnelPipelineResponse()
                                .setCode(pipeline.code())
                                .setName(pipeline.name())
                                .setStages(pipeline.stages().stream().map(stage ->
                                        new DashboardOverviewResponse.SalesFunnelStageResponse()
                                                .setCode(stage.code())
                                                .setName(stage.name())
                                                .setProbability(stage.probability())
                                                .setCount(stage.count())
                                                .setAmount(stage.amount())).toList())).toList());
    }

    private DashboardOverviewResponse.CustomerContactReminderResponse customerContactReminder(
            DashboardOverviewVO.CustomerContactReminderVO value) {
        return new DashboardOverviewResponse.CustomerContactReminderResponse()
                .setStatus(value.status())
                .setBuckets(value.buckets().stream().map(bucket ->
                        new DashboardOverviewResponse.CustomerContactReminderBucketResponse()
                                .setBucket(bucket.bucket())
                                .setCount(bucket.count())).toList());
    }

    private DashboardOverviewResponse.RankingItemResponse rankingItem(
            DashboardOverviewVO.RankingItemVO value) {
        return new DashboardOverviewResponse.RankingItemResponse()
                .setRank(value.rank())
                .setOwnerActorId(value.ownerActorId())
                .setCount(value.count())
                .setAmount(value.amount());
    }

    private DashboardOverviewResponse.TargetResponse target(DashboardOverviewVO.TargetVO value) {
        return new DashboardOverviewResponse.TargetResponse()
                .setMetric(value.metric())
                .setStatus(value.status())
                .setTargetAmount(value.targetAmount())
                .setActualAmount(value.actualAmount())
                .setCompletionRate(value.completionRate());
    }
}
