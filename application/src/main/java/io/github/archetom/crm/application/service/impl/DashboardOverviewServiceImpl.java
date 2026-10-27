package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.DashboardOverviewService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.DashboardOverviewVO;
import io.github.archetom.crm.domain.entity.DashboardPreference;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DashboardDataSummaryMetric;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.ContractExpirationSummary;
import io.github.archetom.crm.domain.model.CustomerFollowUpSummary;
import io.github.archetom.crm.domain.model.CustomerPoolHistorySummary;
import io.github.archetom.crm.domain.model.OpportunityOutcome;
import io.github.archetom.crm.domain.model.DashboardTimeRange;
import io.github.archetom.crm.domain.model.DashboardTimeWindow;
import io.github.archetom.crm.domain.model.FixedReportMetric;
import io.github.archetom.crm.domain.model.PerformanceTargetMetric;
import io.github.archetom.crm.domain.model.ReportRankingValue;
import io.github.archetom.crm.domain.model.ReportTrendPoint;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.DashboardPreferenceRepository;
import io.github.archetom.crm.domain.repository.PerformanceTargetRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Builds each fixed card independently inside one read-only repeatable-read snapshot. */
@Service
@RequiredArgsConstructor
public class DashboardOverviewServiceImpl implements DashboardOverviewService {

    private static final int MAXIMUM_BUCKETS = 120;
    private static final int MAXIMUM_RANKING_ITEMS = 10;
    private static final String AVAILABLE = "AVAILABLE";
    private static final String UNAVAILABLE = "UNAVAILABLE";
    private static final String EMPTY = "EMPTY";

    private final DashboardPreferenceRepository preferenceRepository;
    private final PerformanceTargetRepository performanceTargetRepository;
    private final OrganizationAccessGuard accessGuard;
    private final FixedReportQuerySupport fixedReports;
    private final DashboardSalesBriefQuerySupport salesBriefs;
    private final DashboardSalesFunnelQuerySupport salesFunnels;
    private final DashboardCustomerContactReminderQuerySupport customerContactReminders;
    private final DashboardCustomerPoolHistoryQuerySupport customerPoolHistory;
    private final QueryServiceTemplate queryTemplate;
    private final Clock clock;

    @Override
    public Result<DashboardOverviewVO> get(AuthenticatedCaller caller) {
        Instant requestStartedAt = clock.instant();
        Clock requestClock = Clock.fixed(requestStartedAt, ZoneOffset.UTC);
        return queryTemplate.execute(UseCaseOperation.DASHBOARD_OVERVIEW_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.REPORTS_READ);
                        memberId = new MemberId(caller.actorId());
                    }

                    @Override
                    public DashboardOverviewVO execute() {
                        Selection selection = preferenceRepository
                                .findByMember(tenantId, memberId)
                                .map(Selection::from)
                                .orElseGet(Selection::defaults);
                        DashboardTimeWindow window = DashboardTimeWindow.resolve(
                                selection.timeRange(), selection.customStartDate(),
                                selection.customEndDate(), requestClock);
                        List<LocalDate> periods = window.reportDateRange()
                                .periodStarts(window.granularity(), MAXIMUM_BUCKETS);
                        List<DashboardOverviewVO.CardVO> cards = List.of(
                                card(caller, tenantId, selection.dataScope(), window,
                                        periods, FixedReportMetric.WON_OPPORTUNITY),
                                card(caller, tenantId, selection.dataScope(), window,
                                        periods, FixedReportMetric.ACTIVE_CONTRACT),
                                card(caller, tenantId, selection.dataScope(), window,
                                        periods, FixedReportMetric.ACTIVE_RECEIVABLE));
                        List<DashboardOverviewVO.RankingVO> rankings = List.of(
                                ranking(caller, tenantId, selection.dataScope(), window,
                                        FixedReportMetric.WON_OPPORTUNITY),
                                ranking(caller, tenantId, selection.dataScope(), window,
                                        FixedReportMetric.ACTIVE_CONTRACT),
                                ranking(caller, tenantId, selection.dataScope(), window,
                                        FixedReportMetric.ACTIVE_RECEIVABLE));
                        List<DashboardOverviewVO.SalesBriefVO> salesBrief = salesBriefs.summarize(
                                caller, tenantId, selection.dataScope(), window.reportDateRange(),
                                window.previousReportDateRange().orElse(null)).stream()
                                .map(value -> new DashboardOverviewVO.SalesBriefVO(
                                        value.metric(), value.status(), value.unit(), value.value(),
                                        value.previousValue(), value.comparisonRate()))
                                .toList();
                        List<DashboardOverviewVO.DataSummaryVO> dataSummary = dataSummary(
                                caller, tenantId, selection.dataScope(), window,
                                salesBrief, cards);
                        DashboardOverviewVO.SalesFunnelVO salesFunnel = salesFunnels.summarize(
                                caller, tenantId, selection.dataScope(), window.reportDateRange());
                        DashboardOverviewVO.CustomerContactReminderVO customerContactReminder =
                                customerContactReminders.summarize(caller, tenantId,
                                        selection.dataScope(), LocalDateTime.ofInstant(
                                                requestStartedAt, ZoneOffset.UTC));
                        DashboardOverviewVO.TargetVO target = target(caller, tenantId,
                                memberId, selection.dataScope(), window, cards);
                        return new DashboardOverviewVO(
                                window.asOfDate(), selection.timeRange().name(),
                                window.from(), window.to(), selection.dataScope().name(),
                                window.granularity().name(), cards, salesBrief, dataSummary, salesFunnel,
                                customerContactReminder, rankings, target);
                    }
                });
    }

    private DashboardOverviewVO.CardVO card(
            AuthenticatedCaller caller, TenantId tenantId,
            DashboardDataScope preferredScope, DashboardTimeWindow window,
            List<LocalDate> periods, FixedReportMetric metric) {
        try {
            FixedReportQuerySupport.SourceAccess source =
                    fixedReports.resolveDashboardSource(
                            caller, tenantId, metric, preferredScope);
            fixedReports.requireMetricFields(tenantId, source, false);
            List<ReportTrendPoint> points = fixedReports.completeTrend(
                    periods, fixedReports.trendValues(tenantId, source,
                            window.reportDateRange(), window.granularity()));
            long count = 0L;
            BigDecimal amount = BigDecimal.ZERO.setScale(2);
            for (ReportTrendPoint point : points) {
                count = Math.addExact(count, point.count());
                amount = amount.add(point.amount());
            }
            List<DashboardOverviewVO.PointVO> values = points.stream()
                    .map(point -> new DashboardOverviewVO.PointVO(
                            point.periodStart(), point.count(), point.amount()))
                    .toList();
            return new DashboardOverviewVO.CardVO(
                    metric.name(), AVAILABLE, count, amount, values);
        } catch (NonRetryableApplicationException exception) {
            if (exception.getErrorCode() != ApplicationErrorCode.ACCESS_DENIED) {
                throw exception;
            }
            return new DashboardOverviewVO.CardVO(
                    metric.name(), UNAVAILABLE, null, null, List.of());
        }
    }

    /**
     * Publishes the reference dashboard's closed summary layout without granting any new source
     * access. A metric becomes available only when its existing owning projection is available;
     * metrics without a historical, visibility-safe source stay opaque until that source exists.
     */
    private List<DashboardOverviewVO.DataSummaryVO> dataSummary(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            DashboardTimeWindow window,
            List<DashboardOverviewVO.SalesBriefVO> salesBrief,
            List<DashboardOverviewVO.CardVO> cards) {
        Map<String, DashboardOverviewVO.SalesBriefVO> briefs = salesBrief.stream()
                .collect(Collectors.toMap(DashboardOverviewVO.SalesBriefVO::metric, value -> value));
        Map<String, DashboardOverviewVO.CardVO> cardsByMetric = cards.stream()
                .collect(Collectors.toMap(DashboardOverviewVO.CardVO::metric, value -> value));
        ContractExpirationDataSummary contractExpiration = contractExpirationSummary(
                caller, tenantId, preferredScope, window);
        CustomerFollowUpDataSummary customerFollowUp = customerFollowUpSummary(
                caller, tenantId, preferredScope, window);
        CustomerPoolHistoryDataSummary poolHistory = customerPoolHistorySummary(
                caller, tenantId, preferredScope, window);
        return List.of(DashboardDataSummaryMetric.values()).stream()
                .map(metric -> switch (metric) {
                    case NEW_CUSTOMER -> fromSalesBrief(metric, briefs.get("NEW_CUSTOMER"));
                    case CONVERTED_CUSTOMER -> convertedCustomerSummary(
                            caller, tenantId, preferredScope, window);
                    case NEW_OPPORTUNITY -> fromSalesBrief(metric, briefs.get("NEW_OPPORTUNITY"));
                    case OPPORTUNITY_AMOUNT -> fromSalesBrief(metric, briefs.get("OPPORTUNITY_AMOUNT"));
                    case SIGNED_CONTRACT -> fromSalesBrief(metric, briefs.get("NEW_CONTRACT"));
                    case CONTRACT_AMOUNT -> fromSalesBrief(metric, briefs.get("CONTRACT_AMOUNT"));
                    case RECEIVABLE_AMOUNT -> fromSalesBrief(metric, briefs.get("RECEIVABLE_AMOUNT"));
                    case WON_OPPORTUNITY -> fromCardCount(metric, cardsByMetric.get("WON_OPPORTUNITY"));
                    case LOST_OPPORTUNITY -> lostOpportunitySummary(
                            caller, tenantId, preferredScope, window);
                    case EXPECTED_RECEIVABLE -> expectedReceivableSummary(
                            caller, tenantId, preferredScope, window);
                    case EXPIRING_CONTRACT -> contractExpiration.expiring();
                    case EXPIRED_CONTRACT -> contractExpiration.expired();
                    case FOLLOWED_CUSTOMER -> customerFollowUp.followed();
                    case NEW_UNFOLLOWED_CUSTOMER -> customerFollowUp.newUnfollowed();
                    case CUSTOMER_RELEASED_TO_POOL -> poolHistory.released();
                    case CUSTOMER_CLAIMED_FROM_POOL -> poolHistory.claimed();
                }).toList();
    }

    private DashboardOverviewVO.DataSummaryVO convertedCustomerSummary(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            DashboardTimeWindow window) {
        try {
            DashboardMetricSummary summary = salesBriefs.summarizeConvertedCustomer(
                    caller, tenantId, preferredScope, window.reportDateRange());
            if (summary == null) {
                throw new IllegalStateException("dashboard converted-customer summary is missing");
            }
            return countSummary(DashboardDataSummaryMetric.CONVERTED_CUSTOMER, summary.count());
        } catch (NonRetryableApplicationException exception) {
            if (exception.getErrorCode() != ApplicationErrorCode.ACCESS_DENIED) {
                throw exception;
            }
            return unavailableSummary(DashboardDataSummaryMetric.CONVERTED_CUSTOMER);
        }
    }

    private ContractExpirationDataSummary contractExpirationSummary(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            DashboardTimeWindow window) {
        try {
            ContractExpirationSummary summary = fixedReports.summarizeContractExpiration(
                    caller, tenantId, preferredScope, window.reportDateRange(), window.asOfDate());
            if (summary == null) {
                throw new IllegalStateException("dashboard contract expiration summary is missing");
            }
            return new ContractExpirationDataSummary(
                    countSummary(DashboardDataSummaryMetric.EXPIRING_CONTRACT,
                            summary.expiringCount()),
                    countSummary(DashboardDataSummaryMetric.EXPIRED_CONTRACT,
                            summary.expiredCount()));
        } catch (NonRetryableApplicationException exception) {
            if (exception.getErrorCode() != ApplicationErrorCode.ACCESS_DENIED) {
                throw exception;
            }
            return new ContractExpirationDataSummary(
                    unavailableSummary(DashboardDataSummaryMetric.EXPIRING_CONTRACT),
                    unavailableSummary(DashboardDataSummaryMetric.EXPIRED_CONTRACT));
        }
    }

    private CustomerFollowUpDataSummary customerFollowUpSummary(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            DashboardTimeWindow window) {
        try {
            CustomerFollowUpSummary summary = customerContactReminders.summarizeFollowUpCoverage(
                    caller, tenantId, preferredScope, window.reportDateRange());
            if (summary == null) {
                throw new IllegalStateException("dashboard customer follow-up summary is missing");
            }
            return new CustomerFollowUpDataSummary(
                    countSummary(DashboardDataSummaryMetric.FOLLOWED_CUSTOMER,
                            summary.followedCount()),
                    countSummary(DashboardDataSummaryMetric.NEW_UNFOLLOWED_CUSTOMER,
                            summary.newUnfollowedCount()));
        } catch (NonRetryableApplicationException exception) {
            if (exception.getErrorCode() != ApplicationErrorCode.ACCESS_DENIED) {
                throw exception;
            }
            return new CustomerFollowUpDataSummary(
                    unavailableSummary(DashboardDataSummaryMetric.FOLLOWED_CUSTOMER),
                    unavailableSummary(DashboardDataSummaryMetric.NEW_UNFOLLOWED_CUSTOMER));
        }
    }

    private CustomerPoolHistoryDataSummary customerPoolHistorySummary(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            DashboardTimeWindow window) {
        try {
            CustomerPoolHistorySummary summary = customerPoolHistory.summarize(
                    caller, tenantId, preferredScope, window.reportDateRange());
            if (summary == null) {
                throw new IllegalStateException("dashboard customer pool history summary is missing");
            }
            return new CustomerPoolHistoryDataSummary(
                    countSummary(DashboardDataSummaryMetric.CUSTOMER_RELEASED_TO_POOL,
                            summary.releasedCount()),
                    countSummary(DashboardDataSummaryMetric.CUSTOMER_CLAIMED_FROM_POOL,
                            summary.claimedCount()));
        } catch (NonRetryableApplicationException exception) {
            if (exception.getErrorCode() != ApplicationErrorCode.ACCESS_DENIED) {
                throw exception;
            }
            return new CustomerPoolHistoryDataSummary(
                    unavailableSummary(DashboardDataSummaryMetric.CUSTOMER_RELEASED_TO_POOL),
                    unavailableSummary(DashboardDataSummaryMetric.CUSTOMER_CLAIMED_FROM_POOL));
        }
    }

    private DashboardOverviewVO.DataSummaryVO lostOpportunitySummary(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            DashboardTimeWindow window) {
        try {
            DashboardMetricSummary summary = fixedReports.summarizeOpportunityOutcome(
                    caller, tenantId, preferredScope, OpportunityOutcome.LOST, window.reportDateRange());
            if (summary == null) {
                throw new IllegalStateException("dashboard lost-opportunity summary is missing");
            }
            return new DashboardOverviewVO.DataSummaryVO(
                    DashboardDataSummaryMetric.LOST_OPPORTUNITY.name(), AVAILABLE,
                    DashboardDataSummaryMetric.LOST_OPPORTUNITY.unit(),
                    BigDecimal.valueOf(summary.count()));
        } catch (NonRetryableApplicationException exception) {
            if (exception.getErrorCode() != ApplicationErrorCode.ACCESS_DENIED) {
                throw exception;
            }
            return unavailableSummary(DashboardDataSummaryMetric.LOST_OPPORTUNITY);
        }
    }

    private DashboardOverviewVO.DataSummaryVO expectedReceivableSummary(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            DashboardTimeWindow window) {
        try {
            DashboardMetricSummary summary = fixedReports.summarizeExpectedReceivable(
                    caller, tenantId, preferredScope, window.reportDateRange());
            if (summary == null) {
                throw new IllegalStateException("dashboard expected-receivable summary is missing");
            }
            return new DashboardOverviewVO.DataSummaryVO(
                    DashboardDataSummaryMetric.EXPECTED_RECEIVABLE.name(), AVAILABLE,
                    DashboardDataSummaryMetric.EXPECTED_RECEIVABLE.unit(), summary.amount());
        } catch (NonRetryableApplicationException exception) {
            if (exception.getErrorCode() != ApplicationErrorCode.ACCESS_DENIED) {
                throw exception;
            }
            return unavailableSummary(DashboardDataSummaryMetric.EXPECTED_RECEIVABLE);
        }
    }

    private DashboardOverviewVO.DataSummaryVO fromSalesBrief(
            DashboardDataSummaryMetric metric, DashboardOverviewVO.SalesBriefVO brief) {
        if (brief == null || !AVAILABLE.equals(brief.status()) || brief.value() == null) {
            return unavailableSummary(metric);
        }
        return new DashboardOverviewVO.DataSummaryVO(
                metric.name(), AVAILABLE, metric.unit(), brief.value());
    }

    private DashboardOverviewVO.DataSummaryVO fromCardCount(
                DashboardDataSummaryMetric metric, DashboardOverviewVO.CardVO card) {
        if (card == null || !AVAILABLE.equals(card.status()) || card.count() == null) {
            return unavailableSummary(metric);
        }
        return new DashboardOverviewVO.DataSummaryVO(
                metric.name(), AVAILABLE, metric.unit(), BigDecimal.valueOf(card.count()));
    }

    private DashboardOverviewVO.DataSummaryVO countSummary(
            DashboardDataSummaryMetric metric, long count) {
        return new DashboardOverviewVO.DataSummaryVO(
                metric.name(), AVAILABLE, metric.unit(), BigDecimal.valueOf(count));
    }

    private record ContractExpirationDataSummary(
            DashboardOverviewVO.DataSummaryVO expiring,
            DashboardOverviewVO.DataSummaryVO expired) {
    }

    private record CustomerFollowUpDataSummary(
            DashboardOverviewVO.DataSummaryVO followed,
            DashboardOverviewVO.DataSummaryVO newUnfollowed) {
    }

    private record CustomerPoolHistoryDataSummary(
            DashboardOverviewVO.DataSummaryVO released,
            DashboardOverviewVO.DataSummaryVO claimed) {
    }

    private DashboardOverviewVO.DataSummaryVO unavailableSummary(DashboardDataSummaryMetric metric) {
        return new DashboardOverviewVO.DataSummaryVO(metric.name(), UNAVAILABLE, metric.unit(), null);
    }

    private DashboardOverviewVO.RankingVO ranking(
            AuthenticatedCaller caller, TenantId tenantId,
            DashboardDataScope preferredScope, DashboardTimeWindow window,
            FixedReportMetric metric) {
        try {
            FixedReportQuerySupport.SourceAccess source =
                    fixedReports.resolveDashboardSource(caller, tenantId, metric, preferredScope);
            fixedReports.requireMetricFields(tenantId, source, true);
            List<ReportRankingValue> values = fixedReports.rankingValues(
                    tenantId, source, window.reportDateRange(), MAXIMUM_RANKING_ITEMS);
            List<DashboardOverviewVO.RankingItemVO> items = IntStream.range(0, values.size())
                    .mapToObj(index -> {
                        ReportRankingValue value = values.get(index);
                        return new DashboardOverviewVO.RankingItemVO(index + 1,
                                value.ownerMemberId().getValue(), value.count(), value.amount());
                    })
                    .toList();
            return new DashboardOverviewVO.RankingVO(metric.name(), AVAILABLE, items);
        } catch (NonRetryableApplicationException exception) {
            if (exception.getErrorCode() != ApplicationErrorCode.ACCESS_DENIED) {
                throw exception;
            }
            return new DashboardOverviewVO.RankingVO(metric.name(), UNAVAILABLE, List.of());
        }
    }

    private DashboardOverviewVO.TargetVO target(
            AuthenticatedCaller caller,
            TenantId tenantId,
            MemberId memberId,
            DashboardDataScope preferredScope,
            DashboardTimeWindow window,
            List<DashboardOverviewVO.CardVO> cards) {
        if (!caller.hasAuthority(AuthorityCatalog.DASHBOARD_TARGETS_READ)
                || preferredScope != DashboardDataScope.SELF) {
            return unavailableTarget();
        }
        DashboardOverviewVO.CardVO contract = cards.stream()
                .filter(card -> FixedReportMetric.ACTIVE_CONTRACT.name().equals(card.metric()))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "dashboard contract card is missing"));
        if (!AVAILABLE.equals(contract.status()) || contract.amount() == null) {
            return unavailableTarget();
        }
        BigDecimal configured = configuredTargetAmount(tenantId, memberId, window);
        if (configured.signum() == 0) {
            return new DashboardOverviewVO.TargetVO(
                    PerformanceTargetMetric.ACTIVE_CONTRACT.name(), EMPTY, null, null, null);
        }
        BigDecimal actual = contract.amount();
        BigDecimal completionRate = actual.multiply(BigDecimal.valueOf(100))
                .divide(configured, 2, RoundingMode.HALF_UP);
        return new DashboardOverviewVO.TargetVO(PerformanceTargetMetric.ACTIVE_CONTRACT.name(),
                AVAILABLE, configured, actual, completionRate);
    }

    private BigDecimal configuredTargetAmount(
            TenantId tenantId, MemberId memberId, DashboardTimeWindow window) {
        Set<YearMonth> months = months(window);
        Set<Integer> years = months.stream().map(YearMonth::getYear).collect(Collectors.toSet());
        return years.stream()
                .flatMap(year -> performanceTargetRepository.findByMemberAndYear(
                                tenantId, memberId, PerformanceTargetMetric.ACTIVE_CONTRACT, year)
                        .stream()
                        .filter(target -> months.contains(YearMonth.of(
                                target.getTargetYear(), target.getTargetMonth()))))
                .map(target -> target.getAmount())
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private Set<YearMonth> months(DashboardTimeWindow window) {
        YearMonth first = YearMonth.from(window.from());
        YearMonth last = YearMonth.from(window.to());
        LinkedHashSet<YearMonth> values = new LinkedHashSet<>();
        for (YearMonth current = first; !current.isAfter(last); current = current.plusMonths(1)) {
            values.add(current);
        }
        return Set.copyOf(values);
    }

    private DashboardOverviewVO.TargetVO unavailableTarget() {
        return new DashboardOverviewVO.TargetVO(
                PerformanceTargetMetric.ACTIVE_CONTRACT.name(), UNAVAILABLE, null, null, null);
    }

    private record Selection(
            DashboardTimeRange timeRange,
            LocalDate customStartDate,
            LocalDate customEndDate,
            DashboardDataScope dataScope) {

        private static Selection from(DashboardPreference preference) {
            return new Selection(preference.getTimeRange(), preference.getCustomStartDate(),
                    preference.getCustomEndDate(), preference.getDataScope());
        }

        private static Selection defaults() {
            return new Selection(DashboardPreference.DEFAULT_TIME_RANGE, null, null,
                    DashboardPreference.DEFAULT_DATA_SCOPE);
        }
    }
}
