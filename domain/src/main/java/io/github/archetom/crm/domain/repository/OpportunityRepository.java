package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Opportunity;
import io.github.archetom.crm.domain.model.CustomReportQuery;
import io.github.archetom.crm.domain.model.CustomReportRow;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.DashboardSalesFunnelValue;
import io.github.archetom.crm.domain.model.OpportunityQuery;
import io.github.archetom.crm.domain.model.OpportunityCustomerSummary;
import io.github.archetom.crm.domain.model.OpportunityOutcome;
import io.github.archetom.crm.domain.model.OpportunitySummary;
import io.github.archetom.crm.domain.model.OpportunityVisibility;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.model.ReportRankingValue;
import io.github.archetom.crm.domain.model.ReportTrendPoint;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Tenant-scoped opportunity aggregate persistence and parameterized read predicates. */
public interface OpportunityRepository {

    Opportunity save(
            TenantId tenantId, Opportunity opportunity,
            Collection<FieldDefinition> fieldDefinitions);

    Optional<Opportunity> findById(TenantId tenantId, OpportunityId opportunityId);

    /** Internal B24 worker/root lock after a typed attachment relation proved ownership. */
    Optional<Opportunity> findByIdForAttachmentMutation(TenantId tenantId, OpportunityId opportunityId);

    Optional<Opportunity> findVisibleById(
            TenantId tenantId, OpportunityId opportunityId,
            OpportunityVisibility visibility);

    Optional<Opportunity> findVisibleByIdForUpdate(
            TenantId tenantId, OpportunityId opportunityId, OpportunityVisibility visibility);

    PageResult<Opportunity> findOpportunities(
            TenantId tenantId, OpportunityQuery query);

    List<Opportunity> findVisibleByIds(
            TenantId tenantId, Collection<OpportunityId> opportunityIds,
            OpportunityVisibility visibility, int limit);

    /**
     * Projects only the visible ids under the same predicate.
     *
     * A caller that needs an id set must not pay for the whole aggregate: reusing
     * {@code findVisibleByIds} loads every child collection and then discards it.
     */
    Set<Long> findVisibleIdsIn(
            TenantId tenantId, Collection<OpportunityId> ids,
            OpportunityVisibility visibility, int limit);

    OpportunitySummary summarize(TenantId tenantId, OpportunityQuery query);

    /** Summarizes visible active opportunities created in one bounded dashboard period. */
    DashboardMetricSummary summarizeCreated(
            TenantId tenantId, OpportunityVisibility visibility, ReportDateRange range);

    /** Summarizes one visible, closed outcome by its immutable outcome time in a dashboard period. */
    DashboardMetricSummary summarizeOutcome(
            TenantId tenantId, OpportunityVisibility visibility,
            OpportunityOutcome outcome, ReportDateRange range);

    /** Groups visible active opportunities created in one bounded period by their sales stages. */
    List<DashboardSalesFunnelValue> summarizeSalesFunnel(
            TenantId tenantId, OpportunityVisibility visibility, ReportDateRange range);

    OpportunityCustomerSummary summarizeByCustomer(
            TenantId tenantId, CustomerId customerId, OpportunityVisibility visibility);

    List<ReportTrendPoint> reportWonTrend(
            TenantId tenantId, OpportunityVisibility visibility,
            ReportDateRange range, ReportGranularity granularity);

    List<ReportRankingValue> reportWonRanking(
            TenantId tenantId, OpportunityVisibility visibility,
            ReportDateRange range, int limit);

    List<CustomReportRow> runCustomReport(
            TenantId tenantId, OpportunityVisibility visibility, CustomReportQuery query);
}
