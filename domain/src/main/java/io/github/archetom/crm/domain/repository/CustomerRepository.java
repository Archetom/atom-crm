package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.model.CustomerDistance;
import io.github.archetom.crm.domain.model.CustomerExportQuery;
import io.github.archetom.crm.domain.model.CustomerExportRow;
import io.github.archetom.crm.domain.model.CustomerHierarchyStats;
import io.github.archetom.crm.domain.model.CustomerNearbyQuery;
import io.github.archetom.crm.domain.model.CustomerQuery;
import io.github.archetom.crm.domain.model.CustomerContactReminderSummary;
import io.github.archetom.crm.domain.model.CustomerFollowUpSummary;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.CustomerPoolId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDateTime;

/** Tenant-scoped customer aggregate persistence and parameterized read predicates. */
public interface CustomerRepository {

    Customer save(TenantId tenantId, Customer customer,
                  Collection<FieldDefinition> fieldDefinitions);

    Optional<Customer> findById(TenantId tenantId, CustomerId customerId);

    /** Internal B24 worker/root lock after a typed attachment relation proved ownership. */
    Optional<Customer> findByIdForAttachmentMutation(TenantId tenantId, CustomerId customerId);

    Optional<Customer> findPoolCustomer(
            TenantId tenantId, CustomerPoolId poolId, CustomerId customerId);

    Optional<Customer> findPoolCustomerForUpdate(
            TenantId tenantId, CustomerPoolId poolId, CustomerId customerId);

    List<Customer> findPoolCustomersByIds(
            TenantId tenantId, CustomerPoolId poolId,
            Collection<CustomerId> customerIds, int limit);

    PageResult<Customer> findPoolCustomers(
            TenantId tenantId, CustomerPoolId poolId, String name,
            List<FilterCondition> conditions, int page, int size);

    Optional<Customer> findVisibleById(
            TenantId tenantId, CustomerId customerId, CustomerVisibility visibility);

    Optional<Customer> findVisibleByIdForUpdate(
            TenantId tenantId, CustomerId customerId, CustomerVisibility visibility);

    PageResult<Customer> findCustomers(TenantId tenantId, CustomerQuery query);

    /** Counts visible active customers created in one bounded dashboard period. */
    DashboardMetricSummary summarizeCreated(
            TenantId tenantId, CustomerVisibility visibility, ReportDateRange range);

    /** Counts visible active customers whose durable conversion time falls in one dashboard period. */
    DashboardMetricSummary summarizeConverted(
            TenantId tenantId, CustomerVisibility visibility, ReportDateRange range);

    /** Counts visible active customers by their most recent active customer follow-up. */
    CustomerContactReminderSummary summarizeContactReminders(
            TenantId tenantId, CustomerVisibility visibility, LocalDateTime asOf);

    /** Summarizes visible direct customer follow-up coverage in one dashboard period. */
    CustomerFollowUpSummary summarizeFollowUpCoverage(
            TenantId tenantId, CustomerVisibility visibility, ReportDateRange range);

    /** B30a: tenant-scoped IDs under an explicit read/export visibility intersection. */
    List<CustomerId> findExportCustomerIds(
            TenantId tenantId, CustomerExportQuery query, int limit);

    /** B30a: fixed scalar projection for one already-authorized, bounded ID batch. */
    List<CustomerExportRow> findExportRows(
            TenantId tenantId, Collection<CustomerId> customerIds,
            List<FieldCode> fieldCodes);

    List<Customer> findVisibleByIds(
            TenantId tenantId, Collection<CustomerId> customerIds,
            CustomerVisibility visibility, int limit);

    /**
     * Projects only the visible ids under the same predicate.
     *
     * A caller that needs an id set must not pay for the whole aggregate: reusing
     * {@code findVisibleByIds} loads every child collection and then discards it.
     */
    Set<Long> findVisibleIdsIn(
            TenantId tenantId, Collection<CustomerId> ids,
            CustomerVisibility visibility, int limit);

    List<Customer> findDescendants(TenantId tenantId, CustomerId customerId, int limit);

    Optional<CustomerHierarchyStats> hierarchyStats(
            TenantId tenantId, CustomerId customerId, int nodeLimit, int depthLimit);

    List<Customer> findVisibleDescendants(
            TenantId tenantId, CustomerId customerId,
            CustomerVisibility visibility, int limit);

    List<Customer> findVisibleAncestors(
            TenantId tenantId, CustomerId customerId,
            CustomerVisibility visibility, int limit);

    boolean existsActiveChild(TenantId tenantId, CustomerId customerId);

    boolean existsDuplicate(
            TenantId tenantId, CustomerVisibility visibility, CustomerId excludedCustomerId,
            String name, String phoneNumber, Map<FieldCode, String> uniqueFieldValues);

    List<String> findVisibleTags(TenantId tenantId, CustomerVisibility visibility, int limit);

    PageResult<CustomerDistance> findNearby(TenantId tenantId, CustomerNearbyQuery query);
}
