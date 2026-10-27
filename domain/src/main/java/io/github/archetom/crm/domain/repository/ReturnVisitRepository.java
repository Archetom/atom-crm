package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.ReturnVisit;
import io.github.archetom.crm.domain.model.ReturnVisitQuery;
import io.github.archetom.crm.domain.model.ReturnVisitCustomerSummary;
import io.github.archetom.crm.domain.model.ReturnVisitVisibility;
import io.github.archetom.crm.domain.valueobject.ReturnVisitId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Collection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReturnVisitRepository {
    ReturnVisit save(TenantId tenantId, ReturnVisit value, Collection<FieldDefinition> fields);
    Optional<ReturnVisit> findVisibleById(TenantId tenantId, ReturnVisitId id,
                                          ReturnVisitVisibility visibility);
    Optional<ReturnVisit> findVisibleByIdForUpdate(TenantId tenantId, ReturnVisitId id,
                                                   ReturnVisitVisibility visibility);
    /** Locks the exact root for attachment worker and parent-lifecycle ordering. */
    Optional<ReturnVisit> findByIdForAttachmentMutation(TenantId tenantId, ReturnVisitId id);
    PageResult<ReturnVisit> findReturnVisits(TenantId tenantId, ReturnVisitQuery query);
    ReturnVisitCustomerSummary summarizeByCustomer(
            TenantId tenantId, CustomerId customerId, ReturnVisitVisibility visibility);
    List<ReturnVisit> findOptions(TenantId tenantId, ReturnVisitVisibility visibility,
                                  String keyword, int limit);
    List<ReturnVisit> findScheduled(TenantId tenantId, ReturnVisitVisibility visibility,
                                    LocalDateTime fromInclusive, LocalDateTime untilExclusive,
                                    LocalDateTime afterTime, Long afterId, int limit);
}
