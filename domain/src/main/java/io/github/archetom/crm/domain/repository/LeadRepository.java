package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.Lead;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.LeadDistance;
import io.github.archetom.crm.domain.model.LeadExportQuery;
import io.github.archetom.crm.domain.model.LeadExportRow;
import io.github.archetom.crm.domain.model.LeadHierarchyStats;
import io.github.archetom.crm.domain.model.LeadNearbyQuery;
import io.github.archetom.crm.domain.model.LeadQuery;
import io.github.archetom.crm.domain.model.LeadVisibility;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Tenant-scoped lead aggregate persistence and parameterized read predicates. */
public interface LeadRepository {

    Lead save(TenantId tenantId, Lead lead,
                  Collection<FieldDefinition> fieldDefinitions);

    Optional<Lead> findById(TenantId tenantId, LeadId leadId);

    /** Internal B24 worker/root lock after a typed attachment relation proved ownership. */
    Optional<Lead> findByIdForAttachmentMutation(TenantId tenantId, LeadId leadId);

    Optional<Lead> findVisibleById(
            TenantId tenantId, LeadId leadId, LeadVisibility visibility);

    Optional<Lead> findVisibleByIdForUpdate(
            TenantId tenantId, LeadId leadId, LeadVisibility visibility);

    PageResult<Lead> findLeads(TenantId tenantId, LeadQuery query);

    /** Counts visible active or converted leads created in one bounded dashboard period. */
    DashboardMetricSummary summarizeCreated(
            TenantId tenantId, LeadVisibility visibility, ReportDateRange range);

    /** Tenant-scoped IDs under an explicit Lead read/export visibility intersection. */
    List<LeadId> findExportLeadIds(TenantId tenantId, LeadExportQuery query, int limit);

    /** Fixed scalar projection for one already-authorized, bounded Lead ID batch. */
    List<LeadExportRow> findExportRows(
            TenantId tenantId, Collection<LeadId> leadIds,
            List<FieldCode> fieldCodes);

    List<Lead> findVisibleByIds(
            TenantId tenantId, Collection<LeadId> leadIds,
            LeadVisibility visibility, int limit);

    /**
     * Projects only the visible ids under the same predicate.
     *
     * A caller that needs an id set must not pay for the whole aggregate: reusing
     * {@code findVisibleByIds} loads every child collection and then discards it.
     */
    Set<Long> findVisibleIdsIn(
            TenantId tenantId, Collection<LeadId> ids,
            LeadVisibility visibility, int limit);

    List<Lead> findDescendants(TenantId tenantId, LeadId leadId, int limit);

    Optional<LeadHierarchyStats> hierarchyStats(
            TenantId tenantId, LeadId leadId, int nodeLimit, int depthLimit);

    List<Lead> findVisibleDescendants(
            TenantId tenantId, LeadId leadId,
            LeadVisibility visibility, int limit);

    List<Lead> findVisibleAncestors(
            TenantId tenantId, LeadId leadId,
            LeadVisibility visibility, int limit);

    boolean existsActiveChild(TenantId tenantId, LeadId leadId);

    boolean existsDuplicate(
            TenantId tenantId, LeadVisibility visibility, LeadId excludedLeadId,
            String name, String phoneNumber, Map<FieldCode, String> uniqueFieldValues);

    List<String> findVisibleTags(TenantId tenantId, LeadVisibility visibility, int limit);

    PageResult<LeadDistance> findNearby(TenantId tenantId, LeadNearbyQuery query);
}
