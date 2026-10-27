package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.WorkTaskSeries;
import io.github.archetom.crm.domain.entity.WorkTaskSeriesOccurrence;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Tenant-scoped Work recurrence persistence; occurrence facts stay independently paged. */
public interface WorkTaskSeriesRepository {
    WorkTaskSeries save(TenantId tenantId, WorkTaskSeries series);

    Optional<WorkTaskSeries> findVisibleById(
            TenantId tenantId, WorkTaskSeriesId seriesId, MemberId viewer, boolean manager);

    Optional<WorkTaskSeries> findVisibleByIdForUpdate(
            TenantId tenantId, WorkTaskSeriesId seriesId, MemberId viewer, boolean manager);

    Optional<WorkTaskSeries> findByIdForUpdate(TenantId tenantId, WorkTaskSeriesId seriesId);

    PageResult<WorkTaskSeries> findPage(
            TenantId tenantId, long page, long size, MemberId viewer, boolean manager);

    PageResult<WorkTaskSeriesOccurrence> findOccurrences(
            TenantId tenantId, WorkTaskSeriesId seriesId, long page, long size);

    /** Ordered locks prevent one run from creating unbounded task roots. */
    List<WorkTaskSeries> findDueForUpdate(TenantId tenantId, LocalDateTime evaluationUtc, int limit);

    boolean hasActive(TenantId tenantId);

    long countLive(TenantId tenantId);

    boolean hasOpenGeneratedTask(TenantId tenantId, WorkTaskSeriesId seriesId);

    WorkTaskSeriesOccurrence appendOccurrence(TenantId tenantId, WorkTaskSeriesOccurrence occurrence);
}
