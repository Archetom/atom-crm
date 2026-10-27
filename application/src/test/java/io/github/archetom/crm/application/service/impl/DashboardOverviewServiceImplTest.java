package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.ContractExpirationSummary;
import io.github.archetom.crm.domain.model.CustomerFollowUpSummary;
import io.github.archetom.crm.domain.model.CustomerPoolHistorySummary;
import io.github.archetom.crm.domain.model.FixedReportMetric;
import io.github.archetom.crm.domain.model.OpportunityOutcome;
import io.github.archetom.crm.domain.model.PerformanceTargetMetric;
import io.github.archetom.crm.domain.model.ReportRankingValue;
import io.github.archetom.crm.domain.model.ReportTrendPoint;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.DashboardPreferenceRepository;
import io.github.archetom.crm.domain.repository.PerformanceTargetRepository;
import io.github.archetom.crm.domain.entity.PerformanceTarget;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardOverviewServiceImplTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(
            MEMBER.getValue(), TENANT.getValue(), Set.of(AuthorityCatalog.REPORTS_READ));
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-18T23:30:00Z"), ZoneOffset.UTC);

    @Test
    void defaultsDoNotCreateStateAndDeniedSourcesBecomeOpaqueUnavailableCards() {
        DashboardPreferenceRepository preferences = mock(DashboardPreferenceRepository.class);
        OrganizationAccessGuard accessGuard = mock(OrganizationAccessGuard.class);
        FixedReportQuerySupport reports = mock(FixedReportQuerySupport.class);
        when(accessGuard.requireActiveMembership(CALLER, AuthorityCatalog.REPORTS_READ))
                .thenReturn(TENANT);
        when(preferences.findByMember(TENANT, MEMBER)).thenReturn(Optional.empty());
        FixedReportQuerySupport.SourceAccess opportunity =
                mock(FixedReportQuerySupport.SourceAccess.class);
        FixedReportQuerySupport.SourceAccess receivable =
                mock(FixedReportQuerySupport.SourceAccess.class);
        when(reports.resolveDashboardSource(CALLER, TENANT,
                FixedReportMetric.WON_OPPORTUNITY, DashboardDataScope.SELF))
                .thenReturn(opportunity);
        when(reports.resolveDashboardSource(CALLER, TENANT,
                FixedReportMetric.ACTIVE_CONTRACT, DashboardDataScope.SELF))
                .thenThrow(denied());
        when(reports.resolveDashboardSource(CALLER, TENANT,
                FixedReportMetric.ACTIVE_RECEIVABLE, DashboardDataScope.SELF))
                .thenReturn(receivable);
        doThrow(denied()).when(reports).requireMetricFields(TENANT, receivable, false);
        doThrow(denied()).when(reports).requireMetricFields(TENANT, receivable, true);
        List<ReportTrendPoint> values = List.of(
                new ReportTrendPoint(LocalDate.of(2026, 7, 1), 2L,
                        new BigDecimal("10.10")),
                new ReportTrendPoint(LocalDate.of(2026, 7, 2), 3L,
                        new BigDecimal("20.20")));
        when(reports.completeTrend(any(), any())).thenReturn(values);
        when(reports.rankingValues(eq(TENANT), eq(opportunity), any(), eq(10))).thenReturn(List.of(
                new ReportRankingValue(MEMBER, 5L, new BigDecimal("30.30"))));
        when(reports.summarizeOpportunityOutcome(eq(CALLER), eq(TENANT),
                eq(DashboardDataScope.SELF), eq(OpportunityOutcome.LOST), any()))
                .thenReturn(new DashboardMetricSummary(4L, BigDecimal.ZERO));
        when(reports.summarizeExpectedReceivable(eq(CALLER), eq(TENANT),
                eq(DashboardDataScope.SELF), any()))
                .thenReturn(new DashboardMetricSummary(2L, new BigDecimal("42.42")));
        when(reports.summarizeContractExpiration(eq(CALLER), eq(TENANT),
                eq(DashboardDataScope.SELF), any(), any()))
                .thenReturn(new ContractExpirationSummary(3L, 2L));
        DashboardOverviewServiceImpl service = service(preferences, accessGuard, reports);

        var result = service.get(CALLER);

        assertTrue(result.isSuccess());
        assertEquals(LocalDate.of(2026, 7, 18), result.getData().asOfDate());
        assertEquals("CURRENT_MONTH", result.getData().timeRange());
        assertEquals(LocalDate.of(2026, 7, 1), result.getData().from());
        assertEquals(LocalDate.of(2026, 7, 31), result.getData().to());
        assertEquals("SELF", result.getData().preferredDataScope());
        assertEquals("DAY", result.getData().granularity());
        assertEquals(3, result.getData().cards().size());
        var available = result.getData().cards().getFirst();
        assertEquals("WON_OPPORTUNITY", available.metric());
        assertEquals("AVAILABLE", available.status());
        assertEquals(5L, available.count());
        assertEquals(new BigDecimal("30.30"), available.amount());
        assertEquals(2, available.points().size());
        for (int index : List.of(1, 2)) {
            var unavailable = result.getData().cards().get(index);
            assertEquals("UNAVAILABLE", unavailable.status());
            assertNull(unavailable.count());
            assertNull(unavailable.amount());
            assertTrue(unavailable.points().isEmpty());
        }
        assertEquals(16, result.getData().dataSummary().size());
        var wonOpportunity = result.getData().dataSummary().stream()
                .filter(summary -> "WON_OPPORTUNITY".equals(summary.metric())).findFirst().orElseThrow();
        assertEquals("AVAILABLE", wonOpportunity.status());
        assertEquals(new BigDecimal("5"), wonOpportunity.value());
        var convertedCustomer = result.getData().dataSummary().stream()
                .filter(summary -> "CONVERTED_CUSTOMER".equals(summary.metric()))
                .findFirst().orElseThrow();
        assertEquals("AVAILABLE", convertedCustomer.status());
        assertEquals(new BigDecimal("2"), convertedCustomer.value());
        var releasedCustomer = result.getData().dataSummary().stream()
                .filter(summary -> "CUSTOMER_RELEASED_TO_POOL".equals(summary.metric()))
                .findFirst().orElseThrow();
        assertEquals("AVAILABLE", releasedCustomer.status());
        assertEquals(new BigDecimal("2"), releasedCustomer.value());
        var claimedCustomer = result.getData().dataSummary().stream()
                .filter(summary -> "CUSTOMER_CLAIMED_FROM_POOL".equals(summary.metric()))
                .findFirst().orElseThrow();
        assertEquals("AVAILABLE", claimedCustomer.status());
        assertEquals(new BigDecimal("1"), claimedCustomer.value());
        result.getData().dataSummary().stream()
                .filter(summary -> !"CONVERTED_CUSTOMER".equals(summary.metric())
                        && !"CUSTOMER_RELEASED_TO_POOL".equals(summary.metric())
                        && !"CUSTOMER_CLAIMED_FROM_POOL".equals(summary.metric())
                        && !"WON_OPPORTUNITY".equals(summary.metric())
                        && !"LOST_OPPORTUNITY".equals(summary.metric())
                        && !"EXPECTED_RECEIVABLE".equals(summary.metric())
                        && !"EXPIRING_CONTRACT".equals(summary.metric())
                        && !"EXPIRED_CONTRACT".equals(summary.metric())
                        && !"FOLLOWED_CUSTOMER".equals(summary.metric())
                        && !"NEW_UNFOLLOWED_CUSTOMER".equals(summary.metric())).forEach(summary -> {
            assertEquals("UNAVAILABLE", summary.status());
            assertNull(summary.value());
        });
        var lostOpportunity = result.getData().dataSummary().stream()
                .filter(summary -> "LOST_OPPORTUNITY".equals(summary.metric())).findFirst().orElseThrow();
        assertEquals("AVAILABLE", lostOpportunity.status());
        assertEquals(new BigDecimal("4"), lostOpportunity.value());
        var expectedReceivable = result.getData().dataSummary().stream()
                .filter(summary -> "EXPECTED_RECEIVABLE".equals(summary.metric()))
                .findFirst().orElseThrow();
        assertEquals("AVAILABLE", expectedReceivable.status());
        assertEquals(new BigDecimal("42.42"), expectedReceivable.value());
        var expiringContract = result.getData().dataSummary().stream()
                .filter(summary -> "EXPIRING_CONTRACT".equals(summary.metric()))
                .findFirst().orElseThrow();
        assertEquals("AVAILABLE", expiringContract.status());
        assertEquals(new BigDecimal("3"), expiringContract.value());
        var expiredContract = result.getData().dataSummary().stream()
                .filter(summary -> "EXPIRED_CONTRACT".equals(summary.metric()))
                .findFirst().orElseThrow();
        assertEquals("AVAILABLE", expiredContract.status());
        assertEquals(new BigDecimal("2"), expiredContract.value());
        var followedCustomer = result.getData().dataSummary().stream()
                .filter(summary -> "FOLLOWED_CUSTOMER".equals(summary.metric()))
                .findFirst().orElseThrow();
        assertEquals("AVAILABLE", followedCustomer.status());
        assertEquals(new BigDecimal("6"), followedCustomer.value());
        var newUnfollowedCustomer = result.getData().dataSummary().stream()
                .filter(summary -> "NEW_UNFOLLOWED_CUSTOMER".equals(summary.metric()))
                .findFirst().orElseThrow();
        assertEquals("AVAILABLE", newUnfollowedCustomer.status());
        assertEquals(new BigDecimal("3"), newUnfollowedCustomer.value());
        assertEquals(3, result.getData().rankings().size());
        var ranking = result.getData().rankings().getFirst();
        assertEquals("WON_OPPORTUNITY", ranking.metric());
        assertEquals("AVAILABLE", ranking.status());
        assertEquals(1, ranking.items().size());
        assertEquals(MEMBER.getValue(), ranking.items().getFirst().ownerActorId());
        assertEquals(1, ranking.items().getFirst().rank());
        for (int index : List.of(1, 2)) {
            var unavailable = result.getData().rankings().get(index);
            assertEquals("UNAVAILABLE", unavailable.status());
            assertTrue(unavailable.items().isEmpty());
        }
        assertEquals("UNAVAILABLE", result.getData().target().status());
        assertNull(result.getData().target().targetAmount());
        verify(preferences, never()).save(any(), any());
    }

    @Test
    void selfTargetUsesOnlyTheSameMemberAndSameContractCardSnapshot() {
        AuthenticatedCaller caller = new AuthenticatedCaller(MEMBER.getValue(), TENANT.getValue(),
                Set.of(AuthorityCatalog.REPORTS_READ, AuthorityCatalog.DASHBOARD_TARGETS_READ));
        DashboardPreferenceRepository preferences = mock(DashboardPreferenceRepository.class);
        PerformanceTargetRepository targets = mock(PerformanceTargetRepository.class);
        OrganizationAccessGuard accessGuard = mock(OrganizationAccessGuard.class);
        FixedReportQuerySupport reports = mock(FixedReportQuerySupport.class);
        when(accessGuard.requireActiveMembership(caller, AuthorityCatalog.REPORTS_READ))
                .thenReturn(TENANT);
        when(preferences.findByMember(TENANT, MEMBER)).thenReturn(Optional.empty());
        FixedReportQuerySupport.SourceAccess source = mock(FixedReportQuerySupport.SourceAccess.class);
        for (FixedReportMetric metric : FixedReportMetric.values()) {
            when(reports.resolveDashboardSource(caller, TENANT, metric, DashboardDataScope.SELF))
                    .thenReturn(source);
        }
        when(reports.completeTrend(any(), any())).thenReturn(List.of(
                new ReportTrendPoint(LocalDate.of(2026, 7, 1), 1L,
                        new BigDecimal("30.30"))));
        when(reports.rankingValues(eq(TENANT), eq(source), any(), eq(10))).thenReturn(List.of());
        when(reports.summarizeOpportunityOutcome(eq(caller), eq(TENANT),
                eq(DashboardDataScope.SELF), eq(OpportunityOutcome.LOST), any()))
                .thenThrow(denied());
        when(reports.summarizeExpectedReceivable(eq(caller), eq(TENANT),
                eq(DashboardDataScope.SELF), any())).thenThrow(denied());
        when(reports.summarizeContractExpiration(eq(caller), eq(TENANT),
                eq(DashboardDataScope.SELF), any(), any())).thenThrow(denied());
        when(targets.findByMemberAndYear(TENANT, MEMBER,
                PerformanceTargetMetric.ACTIVE_CONTRACT, 2026)).thenReturn(List.of(
                PerformanceTarget.create(TENANT, MEMBER, PerformanceTargetMetric.ACTIVE_CONTRACT,
                        2026, 7, new BigDecimal("100.00"))));
        DashboardOverviewServiceImpl service = service(preferences, targets, accessGuard, reports, CLOCK);

        var result = service.get(caller);

        assertTrue(result.isSuccess());
        assertEquals("AVAILABLE", result.getData().target().status());
        assertEquals(PerformanceTargetMetric.ACTIVE_CONTRACT.name(), result.getData().target().metric());
        assertEquals(new BigDecimal("100.00"), result.getData().target().targetAmount());
        assertEquals(new BigDecimal("30.30"), result.getData().target().actualAmount());
        assertEquals(new BigDecimal("30.30"), result.getData().target().completionRate());
    }

    @Test
    void unknownInfrastructureFailureStillFailsTheWholeRequest() {
        DashboardPreferenceRepository preferences = mock(DashboardPreferenceRepository.class);
        OrganizationAccessGuard accessGuard = mock(OrganizationAccessGuard.class);
        FixedReportQuerySupport reports = mock(FixedReportQuerySupport.class);
        when(accessGuard.requireActiveMembership(CALLER, AuthorityCatalog.REPORTS_READ))
                .thenReturn(TENANT);
        when(preferences.findByMember(TENANT, MEMBER)).thenReturn(Optional.empty());
        FixedReportQuerySupport.SourceAccess opportunity =
                mock(FixedReportQuerySupport.SourceAccess.class);
        when(reports.resolveDashboardSource(CALLER, TENANT,
                FixedReportMetric.WON_OPPORTUNITY, DashboardDataScope.SELF))
                .thenReturn(opportunity);
        when(reports.trendValues(eq(TENANT), eq(opportunity), any(), any()))
                .thenThrow(new IllegalStateException("database projection failed"));
        DashboardOverviewServiceImpl service = service(preferences, accessGuard, reports);

        var result = service.get(CALLER);

        assertFalse(result.isSuccess());
        verify(reports, never()).resolveDashboardSource(CALLER, TENANT,
                FixedReportMetric.ACTIVE_CONTRACT, DashboardDataScope.SELF);
    }

    @Test
    void capturesUtcTimeBeforePreferenceSnapshotCanCrossMidnight() {
        DashboardPreferenceRepository preferences = mock(DashboardPreferenceRepository.class);
        OrganizationAccessGuard accessGuard = mock(OrganizationAccessGuard.class);
        FixedReportQuerySupport reports = mock(FixedReportQuerySupport.class);
        MutableClock boundaryClock = new MutableClock(
                Instant.parse("2026-07-31T23:59:59.999Z"));
        when(accessGuard.requireActiveMembership(CALLER, AuthorityCatalog.REPORTS_READ))
                .thenReturn(TENANT);
        when(preferences.findByMember(TENANT, MEMBER)).thenAnswer(invocation -> {
            boundaryClock.advanceTo(Instant.parse("2026-08-01T00:00:00Z"));
            return Optional.empty();
        });
        for (FixedReportMetric metric : FixedReportMetric.values()) {
            when(reports.resolveDashboardSource(
                    CALLER, TENANT, metric, DashboardDataScope.SELF))
                    .thenThrow(denied());
        }
        when(reports.summarizeOpportunityOutcome(eq(CALLER), eq(TENANT),
                eq(DashboardDataScope.SELF), eq(OpportunityOutcome.LOST), any()))
                .thenThrow(denied());
        when(reports.summarizeExpectedReceivable(eq(CALLER), eq(TENANT),
                eq(DashboardDataScope.SELF), any())).thenThrow(denied());
        when(reports.summarizeContractExpiration(eq(CALLER), eq(TENANT),
                eq(DashboardDataScope.SELF), any(), any())).thenThrow(denied());
        DashboardOverviewServiceImpl service = service(
                preferences, accessGuard, reports, boundaryClock);

        var result = service.get(CALLER);

        assertTrue(result.isSuccess());
        assertEquals(LocalDate.of(2026, 7, 31), result.getData().asOfDate());
        assertEquals(LocalDate.of(2026, 7, 1), result.getData().from());
        assertEquals(LocalDate.of(2026, 7, 31), result.getData().to());
    }

    private DashboardOverviewServiceImpl service(
            DashboardPreferenceRepository preferences,
            OrganizationAccessGuard accessGuard,
            FixedReportQuerySupport reports) {
        return service(preferences, accessGuard, reports, CLOCK);
    }

    private DashboardOverviewServiceImpl service(
            DashboardPreferenceRepository preferences,
            OrganizationAccessGuard accessGuard,
            FixedReportQuerySupport reports,
            Clock clock) {
        return service(preferences, mock(PerformanceTargetRepository.class), accessGuard, reports, clock);
    }

    private DashboardOverviewServiceImpl service(
            DashboardPreferenceRepository preferences,
            PerformanceTargetRepository targets,
            OrganizationAccessGuard accessGuard,
            FixedReportQuerySupport reports,
            Clock clock) {
        return new DashboardOverviewServiceImpl(preferences, targets, accessGuard, reports,
                convertedCustomers(),
                unavailableFunnels(),
                unavailableCustomerContactReminders(),
                customerPoolHistory(),
                new QueryServiceTemplate("test-app", new TestTransactionManager()), clock);
    }

    private DashboardSalesFunnelQuerySupport unavailableFunnels() {
        DashboardSalesFunnelQuerySupport funnels = mock(DashboardSalesFunnelQuerySupport.class);
        when(funnels.summarize(any(), any(), any(), any())).thenReturn(
                new io.github.archetom.crm.application.vo.DashboardOverviewVO.SalesFunnelVO(
                        "UNAVAILABLE", List.of()));
        return funnels;
    }

    private DashboardSalesBriefQuerySupport convertedCustomers() {
        DashboardSalesBriefQuerySupport briefs = mock(DashboardSalesBriefQuerySupport.class);
        when(briefs.summarizeConvertedCustomer(any(), any(), any(), any())).thenReturn(
                new DashboardMetricSummary(2L, BigDecimal.ZERO));
        return briefs;
    }

    private DashboardCustomerContactReminderQuerySupport unavailableCustomerContactReminders() {
        DashboardCustomerContactReminderQuerySupport reminders =
                mock(DashboardCustomerContactReminderQuerySupport.class);
        when(reminders.summarize(any(), any(), any(), any())).thenReturn(
                new io.github.archetom.crm.application.vo.DashboardOverviewVO
                        .CustomerContactReminderVO("UNAVAILABLE", List.of()));
        when(reminders.summarizeFollowUpCoverage(any(), any(), any(), any())).thenReturn(
                new CustomerFollowUpSummary(6L, 3L));
        return reminders;
    }

    private DashboardCustomerPoolHistoryQuerySupport customerPoolHistory() {
        DashboardCustomerPoolHistoryQuerySupport history =
                mock(DashboardCustomerPoolHistoryQuerySupport.class);
        when(history.summarize(any(), any(), any(), any())).thenReturn(
                new CustomerPoolHistorySummary(2L, 1L));
        return history;
    }

    private NonRetryableApplicationException denied() {
        return new NonRetryableApplicationException(
                ApplicationErrorCode.ACCESS_DENIED, "source is unavailable");
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advanceTo(Instant next) {
            current = next;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("test clock only supports UTC");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
