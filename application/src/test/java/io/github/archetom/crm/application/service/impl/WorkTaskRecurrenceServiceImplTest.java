package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.automation.AutomationExecutionContext;
import io.github.archetom.crm.application.automation.AutomationKind;
import io.github.archetom.crm.application.automation.AutomationScheduleManager;
import io.github.archetom.crm.application.automation.BackgroundWorkObservability;
import io.github.archetom.crm.application.automation.WorkRecurrenceOutcome;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.entity.WorkTaskSeries;
import io.github.archetom.crm.domain.model.WorkRecurrenceFrequency;
import io.github.archetom.crm.domain.model.WorkRecurrenceRule;
import io.github.archetom.crm.domain.model.WorkTaskPriority;
import io.github.archetom.crm.domain.model.WorkTaskSeriesStatus;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;
import io.github.archetom.crm.domain.repository.WorkTaskSeriesRepository;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskSeriesId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkTaskRecurrenceServiceImplTest {
    @Test
    void pausesDerivedScheduleWhenSafetyPauseEndsTheLastActiveSeries() {
        WorkTaskSeriesRepository seriesRepository = mock(WorkTaskSeriesRepository.class);
        OrganizationTenantRepository tenantRepository = mock(OrganizationTenantRepository.class);
        TenantAuthorizationEvaluator authorization = mock(TenantAuthorizationEvaluator.class);
        AutomationScheduleManager scheduleManager = mock(AutomationScheduleManager.class);
        AfterCommitExecutor afterCommit = mock(AfterCommitExecutor.class);
        BackgroundWorkObservability observability = mock(BackgroundWorkObservability.class);
        TenantId tenantId = new TenantId(1L);
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 10, 0);
        AutomationExecutionContext context = context(tenantId, now);
        WorkTaskSeries series = activeSeries(tenantId);
        when(tenantRepository.lockForTenantChanges(tenantId)).thenReturn(true);
        when(seriesRepository.findDueForUpdate(tenantId, now, 100)).thenReturn(List.of(series));
        when(authorization.evaluateAll(eq(tenantId), any())).thenReturn(Map.of());
        when(seriesRepository.hasActive(tenantId)).thenReturn(false);
        doAnswer(invocation -> { invocation.getArgument(1, Runnable.class).run(); return null; })
                .when(afterCommit).execute(org.mockito.ArgumentMatchers.anyString(), any(Runnable.class));

        int materialized = new WorkTaskRecurrenceServiceImpl(seriesRepository, mock(WorkTaskRepository.class),
                tenantRepository, authorization, scheduleManager, afterCommit, observability)
                .materialize(context, 100, () -> now.plusSeconds(1));

        assertEquals(0, materialized);
        assertEquals(WorkTaskSeriesStatus.PAUSED, series.getStatus());
        verify(seriesRepository).save(tenantId, series);
        verify(scheduleManager).synchronizeCurrentRun(context, false);
        verify(observability).recordWorkRecurrenceOutcome(WorkRecurrenceOutcome.PAUSED, 1);
    }

    @Test
    void recordsOpenTaskSkipOnlyAfterTheEffectCommits() {
        WorkTaskSeriesRepository seriesRepository = mock(WorkTaskSeriesRepository.class);
        OrganizationTenantRepository tenantRepository = mock(OrganizationTenantRepository.class);
        TenantAuthorizationEvaluator authorization = mock(TenantAuthorizationEvaluator.class);
        AutomationScheduleManager scheduleManager = mock(AutomationScheduleManager.class);
        AfterCommitExecutor afterCommit = mock(AfterCommitExecutor.class);
        BackgroundWorkObservability observability = mock(BackgroundWorkObservability.class);
        TenantId tenantId = new TenantId(1L);
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 10, 0);
        WorkTaskSeries series = activeSeries(tenantId);
        TenantAuthorizationEvaluator.Evaluation permitted = permitted(tenantId, series.getCreatedByMemberId());
        when(tenantRepository.lockForTenantChanges(tenantId)).thenReturn(true);
        when(seriesRepository.findDueForUpdate(tenantId, now, 100)).thenReturn(List.of(series));
        when(authorization.evaluateAll(eq(tenantId), any())).thenReturn(Map.of(
                series.getCreatedByMemberId(), permitted, series.getCurrentAssigneeMemberId(), permitted));
        when(seriesRepository.hasOpenGeneratedTask(tenantId, series.getId())).thenReturn(true);
        doAnswer(invocation -> { invocation.getArgument(1, Runnable.class).run(); return null; })
                .when(afterCommit).execute(org.mockito.ArgumentMatchers.anyString(), any(Runnable.class));

        int materialized = new WorkTaskRecurrenceServiceImpl(seriesRepository, mock(WorkTaskRepository.class),
                tenantRepository, authorization, scheduleManager, afterCommit, observability)
                .materialize(context(tenantId, now), 100, () -> now.plusSeconds(1));

        assertEquals(0, materialized);
        verify(seriesRepository).appendOccurrence(eq(tenantId), any());
        verify(observability).recordWorkRecurrenceOutcome(WorkRecurrenceOutcome.SKIPPED, 1);
    }

    private static WorkTaskSeries activeSeries(TenantId tenantId) {
        LocalDateTime created = LocalDateTime.of(2026, 7, 22, 0, 0);
        WorkTaskSeries series = WorkTaskSeries.create(tenantId, new MemberId(2L), new MemberId(3L), List.of(),
                "Renew", null, WorkTaskPriority.NORMAL,
                new WorkRecurrenceRule(WorkRecurrenceFrequency.DAILY, 1, Set.of(), null, false,
                        "UTC", LocalDate.of(2026, 7, 22), LocalTime.of(9, 0), null), created);
        series.activate(created);
        series.onPersisted(new WorkTaskSeriesId(8L), 0L, created, created);
        return series;
    }

    private static TenantAuthorizationEvaluator.Evaluation permitted(TenantId tenantId, MemberId memberId) {
        return new TenantAuthorizationEvaluator.Evaluation(
                OrganizationMembership.activate(tenantId, memberId, null), List.of(), List.of(
                RoleGrant.withoutScope(AuthorityCatalog.WORK_TASKS_READ),
                RoleGrant.withoutScope(AuthorityCatalog.WORK_TASKS_WRITE)));
    }

    private static AutomationExecutionContext context(TenantId tenantId, LocalDateTime evaluation) {
        return new AutomationExecutionContext(7L, tenantId, AutomationKind.WORK_TASK_RECURRENCE, 1L,
                UUID.randomUUID(), 1L, 1, evaluation.minusMinutes(5), evaluation);
    }
}
