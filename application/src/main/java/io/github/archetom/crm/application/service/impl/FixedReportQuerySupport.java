package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.DashboardVisibilityNarrower;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.ContractExpirationSummary;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.FixedReportMetric;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.OpportunityOutcome;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.model.ReportRankingValue;
import io.github.archetom.crm.domain.model.ReportTrendPoint;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivablePlanFieldCatalog;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.repository.ReceivablePlanRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Internal B17b/B18b collaboration for the server-owned fixed metrics. */
@Component
@RequiredArgsConstructor
class FixedReportQuerySupport {

    private final OpportunityAccessResolver opportunityAccessResolver;
    private final ContractAccessResolver contractAccessResolver;
    private final ReceivableAccessResolver receivableAccessResolver;
    private final DashboardVisibilityNarrower dashboardVisibilityNarrower;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final OpportunityRepository opportunityRepository;
    private final ContractRepository contractRepository;
    private final ReceivableRepository receivableRepository;
    private final ReceivablePlanRepository receivablePlanRepository;

    SourceAccess resolveSource(
            AuthenticatedCaller caller, TenantId tenantId, FixedReportMetric metric) {
        SourceAccess source = resolveAuthorizedSource(caller, metric);
        requireSameTenant(tenantId, source.tenantId());
        return source;
    }

    SourceAccess resolveDashboardSource(
            AuthenticatedCaller caller, TenantId tenantId, FixedReportMetric metric,
            DashboardDataScope preferredScope) {
        SourceAccess source = resolveAuthorizedSource(caller, metric);
        requireSameDashboardTenant(tenantId, source.tenantId());
        return switch (metric) {
            case WON_OPPORTUNITY -> SourceAccess.opportunity(
                    dashboardVisibilityNarrower.narrow(source.opportunity(), preferredScope));
            case ACTIVE_CONTRACT -> SourceAccess.contract(
                    dashboardVisibilityNarrower.narrow(source.contract(), preferredScope));
            case ACTIVE_RECEIVABLE -> SourceAccess.receivable(
                    dashboardVisibilityNarrower.narrow(source.receivable(), preferredScope));
        };
    }

    private SourceAccess resolveAuthorizedSource(
            AuthenticatedCaller caller, FixedReportMetric metric) {
        if (metric == null) {
            throw new IllegalArgumentException("fixed report metric is required");
        }
        return switch (metric) {
            case WON_OPPORTUNITY -> {
                OpportunityAccessResolver.OpportunityAccess access =
                        opportunityAccessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_READ,
                                OpportunityAccessMode.READ);
                yield SourceAccess.opportunity(access);
            }
            case ACTIVE_CONTRACT -> {
                ContractAccessResolver.ContractAccess access = contractAccessResolver.access(
                        caller, AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ);
                yield SourceAccess.contract(access);
            }
            case ACTIVE_RECEIVABLE -> {
                ReceivableAccessResolver.ReceivableAccess access =
                        receivableAccessResolver.access(caller,
                                AuthorityCatalog.RECEIVABLES_READ,
                                ReceivableAccessMode.READ);
                yield SourceAccess.receivable(access);
            }
        };
    }

    void requireMetricFields(TenantId tenantId, SourceAccess source, boolean ranking) {
        Map<FieldCode, EffectiveFieldAccess> fields = fieldAccessResolver.resolve(
                tenantId, source.memberId(), source.objectType());
        switch (source.metric()) {
            case WON_OPPORTUNITY -> {
                requireReadableUnmasked(fields, OpportunityFieldCatalog.OUTCOME);
                requireReadableUnmasked(fields, OpportunityFieldCatalog.OUTCOME_TIME);
                requireReadableUnmasked(fields, OpportunityFieldCatalog.ESTIMATED_AMOUNT);
                requireReadableUnmasked(fields, OpportunityFieldCatalog.STATUS);
                if (ranking) {
                    requireReadableUnmasked(fields, OpportunityFieldCatalog.OWNER_ACTOR);
                }
            }
            case ACTIVE_CONTRACT -> {
                requireReadableUnmasked(fields, ContractFieldCatalog.STATUS);
                requireReadableUnmasked(fields, ContractFieldCatalog.ORDER_DATE);
                requireReadableUnmasked(fields, ContractFieldCatalog.TOTAL_AMOUNT);
                if (ranking) {
                    requireReadableUnmasked(fields, ContractFieldCatalog.OWNER_ACTOR);
                }
            }
            case ACTIVE_RECEIVABLE -> {
                requireReadableUnmasked(fields, ReceivableFieldCatalog.STATUS);
                requireReadableUnmasked(fields, ReceivableFieldCatalog.RECEIVED_DATE);
                requireReadableUnmasked(fields, ReceivableFieldCatalog.AMOUNT);
                if (ranking) {
                    requireReadableUnmasked(fields, ReceivableFieldCatalog.OWNER_ACTOR);
                }
            }
        }
    }

    List<ReportTrendPoint> trendValues(
            TenantId tenantId, SourceAccess source,
            ReportDateRange range, ReportGranularity granularity) {
        return switch (source.metric()) {
            case WON_OPPORTUNITY -> opportunityRepository.reportWonTrend(
                    tenantId, source.opportunity().visibility(), range, granularity);
            case ACTIVE_CONTRACT -> contractRepository.reportActiveTrend(
                    tenantId, source.contract().visibility(), range, granularity);
            case ACTIVE_RECEIVABLE -> receivableRepository.reportActiveTrend(
                    tenantId, source.receivable().visibility(), range, granularity);
        };
    }

    DashboardMetricSummary summarizeOpportunityOutcome(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            OpportunityOutcome outcome, ReportDateRange range) {
        if (outcome == null || outcome == OpportunityOutcome.OPEN || range == null) {
            throw new IllegalArgumentException("dashboard opportunity outcome is invalid");
        }
        SourceAccess source = resolveDashboardSource(caller, tenantId,
                FixedReportMetric.WON_OPPORTUNITY, preferredScope);
        requireOpportunityOutcomeFields(tenantId, source);
        return opportunityRepository.summarizeOutcome(
                tenantId, source.opportunity().visibility(), outcome, range);
    }

    ContractExpirationSummary summarizeContractExpiration(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            ReportDateRange range, LocalDate asOfDate) {
        if (range == null || asOfDate == null) {
            throw new IllegalArgumentException("dashboard contract expiration window is required");
        }
        SourceAccess source = resolveDashboardSource(caller, tenantId,
                FixedReportMetric.ACTIVE_CONTRACT, preferredScope);
        requireContractExpirationFields(tenantId, source);
        return contractRepository.summarizeExpiration(
                tenantId, source.contract().visibility(), range, asOfDate);
    }

    DashboardMetricSummary summarizeExpectedReceivable(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            ReportDateRange range) {
        if (range == null) {
            throw new IllegalArgumentException("dashboard expected-receivable range is required");
        }
        ReceivableAccessResolver.ReceivableAccess source = receivableAccessResolver.access(
                caller, AuthorityCatalog.RECEIVABLE_PLANS_READ, ReceivableAccessMode.READ);
        requireSameDashboardTenant(tenantId, source.tenantId());
        ReceivableAccessResolver.ReceivableAccess scoped = dashboardVisibilityNarrower.narrow(
                source, preferredScope);
        requireReceivablePlanFields(tenantId, scoped);
        return receivablePlanRepository.summarizeDue(tenantId, scoped.visibility(), range);
    }

    List<ReportRankingValue> rankingValues(
            TenantId tenantId, SourceAccess source,
            ReportDateRange range, int limit) {
        return switch (source.metric()) {
            case WON_OPPORTUNITY -> opportunityRepository.reportWonRanking(
                    tenantId, source.opportunity().visibility(), range, limit);
            case ACTIVE_CONTRACT -> contractRepository.reportActiveRanking(
                    tenantId, source.contract().visibility(), range, limit);
            case ACTIVE_RECEIVABLE -> receivableRepository.reportActiveRanking(
                    tenantId, source.receivable().visibility(), range, limit);
        };
    }

    List<ReportTrendPoint> completeTrend(
            List<LocalDate> periods, List<ReportTrendPoint> values) {
        Set<LocalDate> expected = Set.copyOf(periods);
        Map<LocalDate, ReportTrendPoint> byPeriod = new LinkedHashMap<>();
        for (ReportTrendPoint value : values) {
            if (!expected.contains(value.periodStart())
                    || byPeriod.put(value.periodStart(), value) != null) {
                throw new IllegalStateException("report trend projection is invalid");
            }
        }
        return periods.stream().map(period -> {
            ReportTrendPoint value = byPeriod.get(period);
            return value == null
                    ? new ReportTrendPoint(period, 0L, BigDecimal.ZERO.setScale(2))
                    : value;
        }).toList();
    }

    private void requireSameTenant(TenantId expected, TenantId actual) {
        if (expected == null || actual == null || !expected.sameValueAs(actual)) {
            throw denied("Report source tenant is unavailable");
        }
    }

    private void requireSameDashboardTenant(TenantId expected, TenantId actual) {
        if (expected == null || actual == null || !expected.sameValueAs(actual)) {
            throw new IllegalStateException("dashboard report source tenant is inconsistent");
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

    private void requireOpportunityOutcomeFields(TenantId tenantId, SourceAccess source) {
        if (source == null || source.metric() != FixedReportMetric.WON_OPPORTUNITY
                || source.opportunity() == null) {
            throw new IllegalStateException("dashboard opportunity source is invalid");
        }
        Map<FieldCode, EffectiveFieldAccess> fields = fieldAccessResolver.resolve(
                tenantId, source.memberId(), BusinessObjectType.OPPORTUNITY);
        requireReadableUnmasked(fields, OpportunityFieldCatalog.OUTCOME);
        requireReadableUnmasked(fields, OpportunityFieldCatalog.OUTCOME_TIME);
        requireReadableUnmasked(fields, OpportunityFieldCatalog.STATUS);
    }

    private void requireReceivablePlanFields(
            TenantId tenantId, ReceivableAccessResolver.ReceivableAccess source) {
        if (source == null) {
            throw new IllegalStateException("dashboard receivable-plan source is invalid");
        }
        Map<FieldCode, EffectiveFieldAccess> fields = fieldAccessResolver.resolve(
                tenantId, source.memberId(), BusinessObjectType.RECEIVABLE_PLAN);
        requireReadableUnmasked(fields, ReceivablePlanFieldCatalog.DUE_DATE);
        requireReadableUnmasked(fields, ReceivablePlanFieldCatalog.PLANNED_AMOUNT);
        requireReadableUnmasked(fields, ReceivablePlanFieldCatalog.STATUS);
    }

    private void requireContractExpirationFields(TenantId tenantId, SourceAccess source) {
        if (source == null || source.metric() != FixedReportMetric.ACTIVE_CONTRACT
                || source.contract() == null) {
            throw new IllegalStateException("dashboard contract source is invalid");
        }
        Map<FieldCode, EffectiveFieldAccess> fields = fieldAccessResolver.resolve(
                tenantId, source.memberId(), BusinessObjectType.CONTRACT);
        requireReadableUnmasked(fields, ContractFieldCatalog.END_DATE);
        requireReadableUnmasked(fields, ContractFieldCatalog.STATUS);
    }

    private NonRetryableApplicationException denied(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED, message);
    }

    record SourceAccess(
            FixedReportMetric metric,
            BusinessObjectType objectType,
            MemberId memberId,
            OpportunityAccessResolver.OpportunityAccess opportunity,
            ContractAccessResolver.ContractAccess contract,
            ReceivableAccessResolver.ReceivableAccess receivable) {

        TenantId tenantId() {
            return switch (metric) {
                case WON_OPPORTUNITY -> opportunity.tenantId();
                case ACTIVE_CONTRACT -> contract.tenantId();
                case ACTIVE_RECEIVABLE -> receivable.tenantId();
            };
        }

        static SourceAccess opportunity(
                OpportunityAccessResolver.OpportunityAccess access) {
            return new SourceAccess(FixedReportMetric.WON_OPPORTUNITY,
                    BusinessObjectType.OPPORTUNITY, access.memberId(), access, null, null);
        }

        static SourceAccess contract(ContractAccessResolver.ContractAccess access) {
            return new SourceAccess(FixedReportMetric.ACTIVE_CONTRACT,
                    BusinessObjectType.CONTRACT, access.memberId(), null, access, null);
        }

        static SourceAccess receivable(
                ReceivableAccessResolver.ReceivableAccess access) {
            return new SourceAccess(FixedReportMetric.ACTIVE_RECEIVABLE,
                    BusinessObjectType.RECEIVABLE, access.memberId(), null, null, access);
        }
    }
}
