package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReportRankingRequest;
import io.github.archetom.crm.api.dto.request.ReportTrendRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.application.service.ReportQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.CoreReportVO;
import io.github.archetom.crm.application.vo.ReportRankingVO;
import io.github.archetom.crm.application.vo.ReportTrendVO;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.ContractSummary;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.FixedReportMetric;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.OpportunityOutcome;
import io.github.archetom.crm.domain.model.OpportunityQuery;
import io.github.archetom.crm.domain.model.OpportunitySummary;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.model.ReceivableSummary;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.model.ReportRankingValue;
import io.github.archetom.crm.domain.model.ReportTrendPoint;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes B17 reports without sharing a data scope between
 * different business objects.
 */
@Service
@RequiredArgsConstructor
public class ReportQueryServiceImpl implements ReportQueryService {

    private static final int MAX_TREND_BUCKETS = 120;
    private static final int MAX_TREND_YEARS = 10;
    private static final int MAX_RANKING_DAYS = 366;

    private final OrganizationAccessGuard organizationAccessGuard;
    private final OpportunityAccessResolver opportunityAccessResolver;
    private final ContractAccessResolver contractAccessResolver;
    private final ReceivableAccessResolver receivableAccessResolver;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final OpportunityRepository opportunityRepository;
    private final ContractRepository contractRepository;
    private final ReceivableRepository receivableRepository;
    private final FixedReportQuerySupport fixedReports;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<CoreReportVO> core(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.REPORT_CORE_GET, new ServiceOperation<>() {
            private TenantId tenantId;
            private OpportunityAccessResolver.OpportunityAccess opportunityAccess;
            private ContractAccessResolver.ContractAccess contractAccess;
            private ReceivableAccessResolver.ReceivableAccess receivableAccess;

            @Override
            public void validate() {
                tenantId = organizationAccessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.REPORTS_READ);
                opportunityAccess = opportunityAccessResolver.access(caller,
                        AuthorityCatalog.OPPORTUNITIES_READ, OpportunityAccessMode.READ);
                contractAccess = contractAccessResolver.access(caller,
                        AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ);
                receivableAccess = receivableAccessResolver.access(caller,
                        AuthorityCatalog.RECEIVABLES_READ, ReceivableAccessMode.READ);
                requireSameTenant(tenantId, opportunityAccess.tenantId());
                requireSameTenant(tenantId, contractAccess.tenantId());
                requireSameTenant(tenantId, receivableAccess.tenantId());
            }

            @Override
            public CoreReportVO execute() {
                requireReadableReportFields();
                OpportunitySummary open = opportunityRepository.summarize(tenantId,
                        opportunityQuery(opportunityAccess, OpportunityOutcome.OPEN));
                OpportunitySummary won = opportunityRepository.summarize(tenantId,
                        opportunityQuery(opportunityAccess, OpportunityOutcome.WON));
                OpportunitySummary lost = opportunityRepository.summarize(tenantId,
                        opportunityQuery(opportunityAccess, OpportunityOutcome.LOST));
                ContractSummary contracts = contractRepository.summarize(tenantId,
                        contractAccess.visibility());
                ReceivableSummary receivables = receivableRepository.summarize(tenantId,
                        receivableAccess.visibility());
                return new CoreReportVO(
                        open.count(), open.estimatedAmount(),
                        won.count(), won.estimatedAmount(),
                        lost.count(), lost.estimatedAmount(),
                        contracts.count(), contracts.totalAmount(),
                        receivables.count(), receivables.totalAmount());
            }

            private void requireReadableReportFields() {
                Map<FieldCode, EffectiveFieldAccess> opportunityFields = fieldAccessResolver.resolve(
                        tenantId, opportunityAccess.memberId(), BusinessObjectType.OPPORTUNITY);
                Map<FieldCode, EffectiveFieldAccess> contractFields = fieldAccessResolver.resolve(
                        tenantId, contractAccess.memberId(), BusinessObjectType.CONTRACT);
                Map<FieldCode, EffectiveFieldAccess> receivableFields = fieldAccessResolver.resolve(
                        tenantId, receivableAccess.memberId(), BusinessObjectType.RECEIVABLE);
                requireReadableUnmasked(opportunityFields, OpportunityFieldCatalog.ESTIMATED_AMOUNT);
                requireReadableUnmasked(opportunityFields, OpportunityFieldCatalog.OUTCOME);
                requireReadableUnmasked(contractFields, ContractFieldCatalog.TOTAL_AMOUNT);
                requireReadableUnmasked(contractFields, ContractFieldCatalog.STATUS);
                requireReadableUnmasked(receivableFields, ReceivableFieldCatalog.AMOUNT);
            }
        });
    }

    @Override
    public Result<ReportTrendVO> trend(
            AuthenticatedCaller caller, ReportTrendRequest request) {
        return queryTemplate.execute(UseCaseOperation.REPORT_TREND_QUERY,
                new ServiceOperation<>() {
                    private FixedReportMetric metric;
                    private ReportDateRange range;
                    private ReportGranularity granularity;
                    private List<LocalDate> periods;
                    private TenantId tenantId;
                    private FixedReportQuerySupport.SourceAccess source;

                    @Override
                    public void validate() {
                        if (request == null) {
                            throw new IllegalArgumentException("report trend request is required");
                        }
                        metric = FixedReportMetric.parse(request.getMetric());
                        range = new ReportDateRange(request.getFrom(), request.getTo());
                        granularity = ReportGranularity.parse(request.getGranularity());
                        if (range.exceedsYears(MAX_TREND_YEARS)) {
                            throw new IllegalArgumentException("report trend range is too large");
                        }
                        periods = range.periodStarts(granularity, MAX_TREND_BUCKETS);
                        tenantId = organizationAccessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.REPORTS_READ);
                        source = fixedReports.resolveSource(caller, tenantId, metric);
                    }

                    @Override
                    public ReportTrendVO execute() {
                        fixedReports.requireMetricFields(tenantId, source, false);
                        List<ReportTrendPoint> values = fixedReports.trendValues(
                                tenantId, source, range, granularity);
                        List<ReportTrendVO.ReportTrendPointVO> points = fixedReports
                                .completeTrend(periods, values).stream()
                                .map(value -> new ReportTrendVO.ReportTrendPointVO(
                                        value.periodStart(), value.count(), value.amount()))
                                .toList();
                        return new ReportTrendVO(metric.name(), range.from(), range.to(),
                                granularity.name(), points);
                    }
                });
    }

    @Override
    public Result<ReportRankingVO> ranking(
            AuthenticatedCaller caller, ReportRankingRequest request) {
        return queryTemplate.execute(UseCaseOperation.REPORT_RANKING_QUERY,
                new ServiceOperation<>() {
                    private FixedReportMetric metric;
                    private ReportDateRange range;
                    private int limit;
                    private TenantId tenantId;
                    private FixedReportQuerySupport.SourceAccess source;

                    @Override
                    public void validate() {
                        if (request == null || request.getLimit() == null) {
                            throw new IllegalArgumentException("report ranking request is required");
                        }
                        metric = FixedReportMetric.parse(request.getMetric());
                        range = new ReportDateRange(request.getFrom(), request.getTo());
                        if (range.inclusiveDays() > MAX_RANKING_DAYS) {
                            throw new IllegalArgumentException("report ranking range is too large");
                        }
                        limit = request.getLimit();
                        if (limit < 1 || limit > 100) {
                            throw new IllegalArgumentException("report ranking limit is invalid");
                        }
                        tenantId = organizationAccessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.REPORTS_READ);
                        source = fixedReports.resolveSource(caller, tenantId, metric);
                    }

                    @Override
                    public ReportRankingVO execute() {
                        fixedReports.requireMetricFields(tenantId, source, true);
                        List<ReportRankingValue> values = fixedReports.rankingValues(
                                tenantId, source, range, limit);
                        List<ReportRankingVO.ReportRankingItemVO> items =
                                new ArrayList<>(values.size());
                        for (int index = 0; index < values.size(); index++) {
                            ReportRankingValue value = values.get(index);
                            items.add(new ReportRankingVO.ReportRankingItemVO(
                                    index + 1, value.ownerMemberId().getValue(),
                                    value.count(), value.amount()));
                        }
                        return new ReportRankingVO(metric.name(), range.from(), range.to(),
                                List.copyOf(items));
                    }
                });
    }

    private OpportunityQuery opportunityQuery(
            OpportunityAccessResolver.OpportunityAccess access,
            OpportunityOutcome outcome) {
        return new OpportunityQuery(access.visibility(), null, Map.of(), null, null, null, outcome,
                null, null, List.of(), 1, 1);
    }

    private void requireSameTenant(TenantId expected, TenantId actual) {
        if (actual == null || !expected.sameValueAs(actual)) {
            throw denied("Report source tenant is unavailable");
        }
    }

    private void requireReadableUnmasked(
            Map<FieldCode, EffectiveFieldAccess> accesses, String fieldCode) {
        EffectiveFieldAccess access = accesses.get(new FieldCode(fieldCode));
        if (access == null || access.accessLevel() == FieldAccessLevel.HIDDEN
                || access.maskType() != FieldMaskType.NONE) {
            throw denied("Report field is not readable");
        }
    }

    private NonRetryableApplicationException denied(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED, message);
    }
}
