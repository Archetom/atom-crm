package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Receivable;
import io.github.archetom.crm.domain.model.CustomReportQuery;
import io.github.archetom.crm.domain.model.CustomReportRow;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.ReceivableQuery;
import io.github.archetom.crm.domain.model.ReceivableCustomerSummary;
import io.github.archetom.crm.domain.model.ReceivableSummary;
import io.github.archetom.crm.domain.model.ReceivableVisibility;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.model.ReportRankingValue;
import io.github.archetom.crm.domain.model.ReportTrendPoint;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.ReceivableId;
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ReceivableRepository {
    Receivable save(TenantId tenantId, Receivable value, Collection<FieldDefinition> fields);
    Optional<Receivable> findById(TenantId tenantId, ReceivableId id);
    /** Internal B24 worker/root lock after a typed attachment relation proved ownership. */
    Optional<Receivable> findByIdForAttachmentMutation(TenantId tenantId, ReceivableId id);
    Optional<Receivable> findVisibleById(TenantId tenantId, ReceivableId id,
                                         ReceivableVisibility visibility);
    Optional<Receivable> findVisibleByIdForUpdate(TenantId tenantId, ReceivableId id,
                                                  ReceivableVisibility visibility);
    List<Receivable> findVisibleByIds(TenantId tenantId, Collection<ReceivableId> ids,
                                      ReceivableVisibility visibility, int limit);

    /**
     * Projects only the visible ids under the same predicate.
     *
     * A caller that needs an id set must not pay for the whole aggregate: reusing
     * {@code findVisibleByIds} loads every child collection and then discards it.
     */
    Set<Long> findVisibleIdsIn(
            TenantId tenantId, Collection<ReceivableId> ids,
            ReceivableVisibility visibility, int limit);
    PageResult<Receivable> findReceivables(TenantId tenantId, ReceivableQuery query);
    /** Summarizes the exact visible set selected by a receivable list query. */
    ReceivableSummary summarize(TenantId tenantId, ReceivableQuery query);
    ReceivableSummary summarize(TenantId tenantId, ReceivableVisibility visibility);
    /** Summarizes visible active receipts received in one bounded dashboard period. */
    DashboardMetricSummary summarizeReceived(
            TenantId tenantId, ReceivableVisibility visibility, ReportDateRange range);
    ReceivableCustomerSummary summarizeByCustomer(
            TenantId tenantId, CustomerId customerId, ReceivableVisibility visibility);
    List<ReportTrendPoint> reportActiveTrend(
            TenantId tenantId, ReceivableVisibility visibility,
            ReportDateRange range, ReportGranularity granularity);
    List<ReportRankingValue> reportActiveRanking(
            TenantId tenantId, ReceivableVisibility visibility,
            ReportDateRange range, int limit);
    List<CustomReportRow> runCustomReport(
            TenantId tenantId, ReceivableVisibility visibility, CustomReportQuery query);
    BigDecimal sumActiveAmount(TenantId tenantId, ContractId contractId,
                               ReceivableId excludingId);
    BigDecimal sumActiveAmountByPlan(TenantId tenantId, ReceivablePlanId planId,
                                     ReceivableId excludingId);
    Map<ReceivablePlanId, BigDecimal> sumActiveAmountsByPlan(
            TenantId tenantId, Collection<ReceivablePlanId> planIds);
    boolean existsByContract(TenantId tenantId, ContractId contractId);
    boolean existsByPlan(TenantId tenantId, ReceivablePlanId planId);
}
