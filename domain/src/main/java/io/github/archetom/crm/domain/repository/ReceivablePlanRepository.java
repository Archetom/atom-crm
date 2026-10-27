package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.ReceivablePlan;
import io.github.archetom.crm.domain.model.ReceivablePlanQuery;
import io.github.archetom.crm.domain.model.ReceivablePlanCustomerSummary;
import io.github.archetom.crm.domain.model.ReceivablePlanSummary;
import io.github.archetom.crm.domain.model.ReceivableVisibility;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ReceivablePlanRepository {
    ReceivablePlan save(TenantId tenantId, ReceivablePlan value, Collection<FieldDefinition> fields);
    Optional<ReceivablePlan> findById(TenantId tenantId, ReceivablePlanId id);
    /** Internal B24 worker/root lock after a typed attachment relation proved ownership. */
    Optional<ReceivablePlan> findByIdForAttachmentMutation(TenantId tenantId, ReceivablePlanId id);
    Optional<ReceivablePlan> findVisibleById(TenantId tenantId, ReceivablePlanId id,
                                             ReceivableVisibility visibility);
    Optional<ReceivablePlan> findVisibleByIdForUpdate(TenantId tenantId, ReceivablePlanId id,
                                                      ReceivableVisibility visibility);
    List<ReceivablePlan> findVisibleByIds(TenantId tenantId, Collection<ReceivablePlanId> ids,
                                          ReceivableVisibility visibility, int limit);

    /**
     * Projects only the visible ids under the same predicate.
     *
     * A caller that needs an id set must not pay for the whole aggregate: reusing
     * {@code findVisibleByIds} loads every child collection and then discards it.
     */
    Set<Long> findVisibleIdsIn(
            TenantId tenantId, Collection<ReceivablePlanId> ids,
            ReceivableVisibility visibility, int limit);
    PageResult<ReceivablePlan> findPlans(TenantId tenantId, ReceivablePlanQuery query);
    /**
     * Returns a bounded, stable export projection restricted by both the read and export scopes.
     * The caller passes a validated query; the repository applies the second visibility predicate.
     */
    List<ReceivablePlan> findExportPlans(TenantId tenantId, ReceivablePlanQuery query,
                                         ReceivableVisibility exportVisibility, int limit);
    /**
     * Aggregates the same predicate as {@link #findPlans(TenantId, ReceivablePlanQuery)}.
     * Received money is additionally restricted by the independently resolved receivable scope.
     */
    ReceivablePlanSummary summarize(
            TenantId tenantId, ReceivablePlanQuery query, ReceivableVisibility receivableVisibility);
    ReceivablePlanCustomerSummary summarizeByCustomer(
            TenantId tenantId, CustomerId customerId, ReceivableVisibility visibility);
    /** Sums visible active planned amounts whose due date falls in the selected dashboard window. */
    DashboardMetricSummary summarizeDue(
            TenantId tenantId, ReceivableVisibility visibility, ReportDateRange range);
    List<ReceivablePlan> findAvailableByContract(TenantId tenantId, ContractId contractId,
                                                  ReceivableVisibility visibility);
    BigDecimal sumActivePlannedAmount(TenantId tenantId, ContractId contractId,
                                      ReceivablePlanId excludingId);
    int nextInstallment(TenantId tenantId, ContractId contractId);
    boolean existsByContract(TenantId tenantId, ContractId contractId);
}
