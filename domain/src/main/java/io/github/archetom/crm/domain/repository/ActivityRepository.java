package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.Activity;
import io.github.archetom.crm.domain.model.ActivitySubjectSummary;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.valueobject.ActivityId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ActivityRepository {
    Activity save(TenantId tenantId, Activity activity);
    Optional<Activity> findById(TenantId tenantId, ActivityId id);
    PageResult<Activity> findBySubject(TenantId tenantId, BusinessObjectType subjectType,
                                       Long subjectId, int page, int size);
    List<ActivitySubjectSummary> findLatestBySubjects(TenantId tenantId,
                                                       BusinessObjectType subjectType,
                                                       Collection<Long> subjectIds);
    /** Counts one member's active follow-up records created in one bounded dashboard period. */
    DashboardMetricSummary summarizeCreatedFollowUps(
            TenantId tenantId, MemberId creatorMemberId, ReportDateRange range);
    List<Activity> findDue(TenantId tenantId, LocalDateTime fromInclusive,
                           LocalDateTime untilExclusive, LocalDateTime afterTime,
                           Long afterId, int limit);
}
