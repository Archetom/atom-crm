package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.Contract;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.CustomReportQuery;
import io.github.archetom.crm.domain.model.CustomReportRow;
import io.github.archetom.crm.domain.model.ContractQuery;
import io.github.archetom.crm.domain.model.ContractListSummary;
import io.github.archetom.crm.domain.model.ContractCustomerSummary;
import io.github.archetom.crm.domain.model.ContractExpirationSummary;
import io.github.archetom.crm.domain.model.ContractSummary;
import io.github.archetom.crm.domain.model.ContractVisibility;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.model.ReportRankingValue;
import io.github.archetom.crm.domain.model.ReportTrendPoint;
import io.github.archetom.crm.domain.model.ReceivableVisibility;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ContractRepository {
    Contract save(
            TenantId tenantId, Contract contract,
            Collection<FieldDefinition> fieldDefinitions);

    Optional<Contract> findById(TenantId tenantId, ContractId contractId);

    Optional<Contract> findByIdForUpdate(TenantId tenantId, ContractId contractId);

    /** Alias that makes B24's internal typed-parent lock explicit at its call sites. */
    default Optional<Contract> findByIdForAttachmentMutation(TenantId tenantId, ContractId contractId) {
        return findByIdForUpdate(tenantId, contractId);
    }

    Optional<Contract> findVisibleById(
            TenantId tenantId, ContractId contractId, ContractVisibility visibility);

    Optional<Contract> findVisibleByIdForUpdate(
            TenantId tenantId, ContractId contractId, ContractVisibility visibility);

    List<Contract> findVisibleByIds(
            TenantId tenantId, Collection<ContractId> contractIds,
            ContractVisibility visibility, int limit);

    /**
     * Projects only the visible ids under the same predicate.
     *
     * A caller that needs an id set must not pay for the whole aggregate: reusing
     * {@code findVisibleByIds} loads every child collection and then discards it.
     */
    Set<Long> findVisibleIdsIn(
            TenantId tenantId, Collection<ContractId> ids,
            ContractVisibility visibility, int limit);

    PageResult<Contract> findContracts(TenantId tenantId, ContractQuery query);

    /** Summarizes the exact visible set selected by a contract list query. */
    ContractListSummary summarize(
            TenantId tenantId, ContractQuery query, ReceivableVisibility receivableVisibility);

    ContractSummary summarize(TenantId tenantId, ContractVisibility visibility);

    /** Summarizes visible active contracts created in one bounded dashboard period. */
    DashboardMetricSummary summarizeCreated(
            TenantId tenantId, ContractVisibility visibility, ReportDateRange range);

    /** Splits visible active contracts ending in the dashboard period at its fixed as-of date. */
    ContractExpirationSummary summarizeExpiration(
            TenantId tenantId, ContractVisibility visibility, ReportDateRange range,
            java.time.LocalDate asOfDate);

    ContractCustomerSummary summarizeByCustomer(
            TenantId tenantId, CustomerId customerId, ContractVisibility visibility);

    List<ReportTrendPoint> reportActiveTrend(
            TenantId tenantId, ContractVisibility visibility,
            ReportDateRange range, ReportGranularity granularity);

    List<ReportRankingValue> reportActiveRanking(
            TenantId tenantId, ContractVisibility visibility,
            ReportDateRange range, int limit);

    List<CustomReportRow> runCustomReport(
            TenantId tenantId, ContractVisibility visibility, CustomReportQuery query);
}
