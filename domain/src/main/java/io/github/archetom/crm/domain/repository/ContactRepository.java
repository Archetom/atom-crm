package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.model.ContactDistance;
import io.github.archetom.crm.domain.model.ContactExportQuery;
import io.github.archetom.crm.domain.model.ContactExportRow;
import io.github.archetom.crm.domain.model.ContactHierarchyStats;
import io.github.archetom.crm.domain.model.ContactNearbyQuery;
import io.github.archetom.crm.domain.model.ContactQuery;
import io.github.archetom.crm.domain.model.ContactCustomerSummary;
import io.github.archetom.crm.domain.model.ContactVisibility;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContactPoolId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Tenant-scoped contact aggregate persistence and parameterized read predicates. */
public interface ContactRepository {

    Contact save(TenantId tenantId, Contact contact,
                  Collection<FieldDefinition> fieldDefinitions);

    Optional<Contact> findById(TenantId tenantId, ContactId contactId);

    /** Internal B24 worker/root lock after a typed attachment relation proved ownership. */
    Optional<Contact> findByIdForAttachmentMutation(TenantId tenantId, ContactId contactId);

    Optional<Contact> findPoolContact(
            TenantId tenantId, ContactPoolId poolId, ContactId contactId);

    Optional<Contact> findPoolContactForUpdate(
            TenantId tenantId, ContactPoolId poolId, ContactId contactId);

    List<Contact> findPoolContactsByIds(
            TenantId tenantId, ContactPoolId poolId,
            Collection<ContactId> contactIds, int limit);

    PageResult<Contact> findPoolContacts(
            TenantId tenantId, ContactPoolId poolId, String name,
            List<FilterCondition> conditions, int page, int size);

    Optional<Contact> findVisibleById(
            TenantId tenantId, ContactId contactId, ContactVisibility visibility);

    Optional<Contact> findVisibleByIdForUpdate(
            TenantId tenantId, ContactId contactId, ContactVisibility visibility);

    PageResult<Contact> findContacts(TenantId tenantId, ContactQuery query);

    /** Counts visible active contacts created in one bounded dashboard period. */
    DashboardMetricSummary summarizeCreated(
            TenantId tenantId, ContactVisibility visibility, ReportDateRange range);

    /** B31a: tenant-scoped IDs under an explicit Contact read/export visibility intersection. */
    List<ContactId> findExportContactIds(
            TenantId tenantId, ContactExportQuery query, int limit);

    /** B31a: fixed scalar projection for one already-authorized, bounded Contact ID batch. */
    List<ContactExportRow> findExportRows(
            TenantId tenantId, Collection<ContactId> contactIds,
            List<FieldCode> fieldCodes);

    ContactCustomerSummary summarizeByCustomer(
            TenantId tenantId, CustomerId customerId, ContactVisibility visibility);

    List<Contact> findVisibleByIds(
            TenantId tenantId, Collection<ContactId> contactIds,
            ContactVisibility visibility, int limit);

    /**
     * Projects only the visible ids under the same predicate.
     *
     * A caller that needs an id set must not pay for the whole aggregate: reusing
     * {@code findVisibleByIds} loads every child collection and then discards it.
     */
    Set<Long> findVisibleIdsIn(
            TenantId tenantId, Collection<ContactId> ids,
            ContactVisibility visibility, int limit);

    List<Contact> findDescendants(TenantId tenantId, ContactId contactId, int limit);

    Optional<ContactHierarchyStats> hierarchyStats(
            TenantId tenantId, ContactId contactId, int nodeLimit, int depthLimit);

    List<Contact> findVisibleDescendants(
            TenantId tenantId, ContactId contactId,
            ContactVisibility visibility, int limit);

    List<Contact> findVisibleAncestors(
            TenantId tenantId, ContactId contactId,
            ContactVisibility visibility, int limit);

    boolean existsActiveChild(TenantId tenantId, ContactId contactId);

    boolean existsDuplicate(
            TenantId tenantId, ContactVisibility visibility, ContactId excludedContactId,
            String name, String phoneNumber, Map<FieldCode, String> uniqueFieldValues);

    List<String> findVisibleTags(TenantId tenantId, ContactVisibility visibility, int limit);

    PageResult<ContactDistance> findNearby(TenantId tenantId, ContactNearbyQuery query);
}
