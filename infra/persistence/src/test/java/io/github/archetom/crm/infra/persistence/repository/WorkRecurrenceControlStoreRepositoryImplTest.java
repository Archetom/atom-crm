package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.application.automation.WorkRecurrencePolicy;
import io.github.archetom.crm.application.automation.WorkRecurrenceReadiness;
import io.github.archetom.crm.application.automation.AutomationKind;
import io.github.archetom.crm.application.automation.AutomationScheduleManager;
import io.github.archetom.crm.domain.exception.WorkTaskDomainException;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AutomationScheduleMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkRecurrenceControlMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskSeriesMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskSeriesWeekdayMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkRecurrenceControlPO;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskSeriesPO;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkRecurrenceControlStoreRepositoryImplTest {
    @Test
    void recalculatesEveryActiveUtcCursorBeforePublishingANewerPolicyRevision() {
        WorkRecurrenceControlMapper control = mock(WorkRecurrenceControlMapper.class);
        AutomationScheduleMapper automation = mock(AutomationScheduleMapper.class);
        WorkTaskSeriesMapper series = mock(WorkTaskSeriesMapper.class);
        WorkTaskSeriesWeekdayMapper weekdays = mock(WorkTaskSeriesWeekdayMapper.class);
        AutomationScheduleManager schedules = mock(AutomationScheduleManager.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        when(automation.selectUtcNow()).thenReturn(now);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        when(control.selectForUpdate()).thenReturn(control("B27A_V1", "TZDB_OLD"));
        when(control.startReconciliation("TZDB_NEW", "B27A_V2")).thenReturn(1);
        when(series.selectActiveForRecurrenceReconciliation(null, null, 100)).thenReturn(List.of(activeSeries()));
        when(series.selectActiveForRecurrenceReconciliationForUpdate(List.of(9L))).thenReturn(List.of(activeSeries()));
        when(weekdays.selectForRecurrenceReconciliation(List.of(9L))).thenReturn(List.of());
        when(series.updateActiveNextDueAt(eq(8L), eq(9L), eq(LocalDateTime.of(2026, 11, 1, 5, 30))))
                .thenReturn(1);
        when(control.publishReady("TZDB_NEW", "B27A_V2", now)).thenReturn(1);

        WorkRecurrenceReadiness readiness = new WorkRecurrenceControlStoreRepositoryImpl(control, automation,
                series, weekdays, schedules, transactions).reconcile(new WorkRecurrencePolicy(true, true, "B27A_V2", "TZDB_NEW"));

        assertEquals(WorkRecurrenceReadiness.READY, readiness);
        var order = inOrder(series, control);
        order.verify(series).updateActiveNextDueAt(8L, 9L, LocalDateTime.of(2026, 11, 1, 5, 30));
        order.verify(control).publishReady("TZDB_NEW", "B27A_V2", now);
    }

    @Test
    void checkpointsABoundedBatchWithoutPublishingUntilTheNextBatchFinishes() {
        WorkRecurrenceControlMapper control = mock(WorkRecurrenceControlMapper.class);
        AutomationScheduleMapper automation = mock(AutomationScheduleMapper.class);
        WorkTaskSeriesMapper series = mock(WorkTaskSeriesMapper.class);
        WorkTaskSeriesWeekdayMapper weekdays = mock(WorkTaskSeriesWeekdayMapper.class);
        AutomationScheduleManager schedules = mock(AutomationScheduleManager.class);
        PlatformTransactionManager transactions = transactions();
        List<WorkTaskSeriesPO> batch = java.util.stream.LongStream.rangeClosed(9, 108)
                .mapToObj(WorkRecurrenceControlStoreRepositoryImplTest::activeSeries).toList();
        when(automation.selectUtcNow()).thenReturn(LocalDateTime.of(2026, 7, 22, 12, 0));
        when(control.selectForUpdate()).thenReturn(reconciling("B27A_V1", "TZDB_OLD", 8L, 8L));
        when(series.selectActiveForRecurrenceReconciliation(8L, 8L, 100)).thenReturn(batch);
        when(series.selectActiveForRecurrenceReconciliationForUpdate(batch.stream().map(WorkTaskSeriesPO::getId).toList()))
                .thenReturn(batch);
        when(weekdays.selectForRecurrenceReconciliation(batch.stream().map(WorkTaskSeriesPO::getId).toList()))
                .thenReturn(List.of());
        when(series.updateActiveNextDueAt(any(Long.class), any(Long.class), any(LocalDateTime.class))).thenReturn(1);
        when(control.updateCursor(8L, 108L)).thenReturn(1);

        WorkRecurrenceReadiness readiness = new WorkRecurrenceControlStoreRepositoryImpl(control, automation,
                series, weekdays, schedules, transactions).reconcile(new WorkRecurrencePolicy(true, true, "B27A_V2", "TZDB_NEW"));

        assertEquals(WorkRecurrenceReadiness.RECONCILING, readiness);
        verify(control).updateCursor(8L, 108L);
        verify(control, never()).publishReady(any(), any(), any());
    }

    @Test
    void pausesTheLastTenantScheduleWhenReconciliationEndsItsOnlySeries() {
        WorkRecurrenceControlMapper control = mock(WorkRecurrenceControlMapper.class);
        AutomationScheduleMapper automation = mock(AutomationScheduleMapper.class);
        WorkTaskSeriesMapper series = mock(WorkTaskSeriesMapper.class);
        WorkTaskSeriesWeekdayMapper weekdays = mock(WorkTaskSeriesWeekdayMapper.class);
        AutomationScheduleManager schedules = mock(AutomationScheduleManager.class);
        PlatformTransactionManager transactions = transactions();
        WorkTaskSeriesPO exhausted = activeSeries();
        exhausted.setZoneId("Pacific/Pago_Pago");
        exhausted.setStartLocalDate(LocalDate.of(9999, 12, 31));
        exhausted.setLocalTime(LocalTime.of(23, 30));
        exhausted.setNextNominalLocalTime(LocalDateTime.of(9999, 12, 31, 23, 30));
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        when(automation.selectUtcNow()).thenReturn(now);
        when(control.selectForUpdate()).thenReturn(control("B27A_V1", "TZDB_OLD"));
        when(control.startReconciliation("TZDB_NEW", "B27A_V2")).thenReturn(1);
        when(series.selectActiveForRecurrenceReconciliation(null, null, 100)).thenReturn(List.of(exhausted));
        when(series.selectActiveForRecurrenceReconciliationForUpdate(List.of(9L))).thenReturn(List.of(exhausted));
        when(weekdays.selectForRecurrenceReconciliation(List.of(9L))).thenReturn(List.of());
        when(series.endForRecurrenceTimeRange(8L, 9L)).thenReturn(1);
        when(series.countActiveForRecurrenceReconciliation(8L)).thenReturn(0L);
        when(control.publishReady("TZDB_NEW", "B27A_V2", now)).thenReturn(1);

        WorkRecurrenceReadiness readiness = new WorkRecurrenceControlStoreRepositoryImpl(control, automation,
                series, weekdays, schedules, transactions).reconcile(new WorkRecurrencePolicy(true, true, "B27A_V2", "TZDB_NEW"));

        assertEquals(WorkRecurrenceReadiness.READY, readiness);
        verify(schedules).synchronize(new TenantId(8L), AutomationKind.WORK_TASK_RECURRENCE, false);
    }

    @Test
    void leavesTheControlUnpublishedWhenPersistedRuleDataCannotBeInterpreted() {
        WorkRecurrenceControlMapper control = mock(WorkRecurrenceControlMapper.class);
        AutomationScheduleMapper automation = mock(AutomationScheduleMapper.class);
        WorkTaskSeriesMapper series = mock(WorkTaskSeriesMapper.class);
        WorkTaskSeriesWeekdayMapper weekdays = mock(WorkTaskSeriesWeekdayMapper.class);
        AutomationScheduleManager schedules = mock(AutomationScheduleManager.class);
        WorkTaskSeriesPO invalid = activeSeries();
        invalid.setZoneId("Unknown/Zone");
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        when(automation.selectUtcNow()).thenReturn(now);
        when(control.selectForUpdate()).thenReturn(control("B27A_V1", "TZDB_OLD"));
        when(control.startReconciliation("TZDB_NEW", "B27A_V2")).thenReturn(1);
        when(series.selectActiveForRecurrenceReconciliation(null, null, 100)).thenReturn(List.of(invalid));
        when(series.selectActiveForRecurrenceReconciliationForUpdate(List.of(9L))).thenReturn(List.of(invalid));
        when(weekdays.selectForRecurrenceReconciliation(List.of(9L))).thenReturn(List.of());

        assertThrows(WorkTaskDomainException.class, () -> new WorkRecurrenceControlStoreRepositoryImpl(control, automation,
                series, weekdays, schedules, transactions()).reconcile(new WorkRecurrencePolicy(true, true, "B27A_V2", "TZDB_NEW")));

        verify(series, never()).endForRecurrenceTimeRange(8L, 9L);
        verify(control, never()).publishReady(any(), any(), any());
    }

    @Test
    void doesNotPublishTheTargetRevisionWhenTheStoredCursorIsNotAnOccurrenceOfItsRule() {
        WorkRecurrenceControlMapper control = mock(WorkRecurrenceControlMapper.class);
        AutomationScheduleMapper automation = mock(AutomationScheduleMapper.class);
        WorkTaskSeriesMapper series = mock(WorkTaskSeriesMapper.class);
        WorkTaskSeriesWeekdayMapper weekdays = mock(WorkTaskSeriesWeekdayMapper.class);
        AutomationScheduleManager schedules = mock(AutomationScheduleManager.class);
        WorkTaskSeriesPO invalidCursor = activeSeries();
        invalidCursor.setNextNominalLocalTime(LocalDateTime.of(2026, 11, 1, 1, 31));
        invalidCursor.setNextDueAt(LocalDateTime.of(2026, 11, 1, 5, 31));
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        when(automation.selectUtcNow()).thenReturn(now);
        when(control.selectForUpdate()).thenReturn(control("B27A_V1", "TZDB_OLD"));
        when(control.startReconciliation("TZDB_NEW", "B27A_V2")).thenReturn(1);
        when(series.selectActiveForRecurrenceReconciliation(null, null, 100)).thenReturn(List.of(invalidCursor));
        when(series.selectActiveForRecurrenceReconciliationForUpdate(List.of(9L))).thenReturn(List.of(invalidCursor));
        when(weekdays.selectForRecurrenceReconciliation(List.of(9L))).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> new WorkRecurrenceControlStoreRepositoryImpl(control, automation,
                series, weekdays, schedules, transactions()).reconcile(new WorkRecurrencePolicy(true, true, "B27A_V2", "TZDB_NEW")));

        verify(series, never()).updateActiveNextDueAt(any(Long.class), any(Long.class), any(LocalDateTime.class));
        verify(control, never()).publishReady(any(), any(), any());
    }

    @Test
    void newerRevisionReplacesAnInterruptedOlderReconciliationFromTheFirstBatch() {
        WorkRecurrenceControlMapper control = mock(WorkRecurrenceControlMapper.class);
        AutomationScheduleMapper automation = mock(AutomationScheduleMapper.class);
        WorkTaskSeriesMapper series = mock(WorkTaskSeriesMapper.class);
        WorkTaskSeriesWeekdayMapper weekdays = mock(WorkTaskSeriesWeekdayMapper.class);
        AutomationScheduleManager schedules = mock(AutomationScheduleManager.class);
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        when(automation.selectUtcNow()).thenReturn(now);
        when(control.selectForUpdate()).thenReturn(reconciling("B27A_V1", "TZDB_OLD", 8L, 8L));
        when(control.startReconciliation("TZDB_V3", "B27A_V3")).thenReturn(1);
        when(series.selectActiveForRecurrenceReconciliation(null, null, 100)).thenReturn(List.of());
        when(control.publishReady("TZDB_V3", "B27A_V3", now)).thenReturn(1);

        WorkRecurrenceReadiness readiness = new WorkRecurrenceControlStoreRepositoryImpl(control, automation,
                series, weekdays, schedules, transactions()).reconcile(new WorkRecurrencePolicy(true, true, "B27A_V3", "TZDB_V3"));

        assertEquals(WorkRecurrenceReadiness.READY, readiness);
        verify(control).startReconciliation("TZDB_V3", "B27A_V3");
        verify(series).selectActiveForRecurrenceReconciliation(null, null, 100);
        verify(control).publishReady("TZDB_V3", "B27A_V3", now);
    }

    @Test
    void disabledPolicyWinsOverInvalidUnusedPolicyFields() {
        WorkRecurrenceControlStoreRepositoryImpl repository = new WorkRecurrenceControlStoreRepositoryImpl(
                mock(WorkRecurrenceControlMapper.class), mock(AutomationScheduleMapper.class),
                mock(WorkTaskSeriesMapper.class), mock(WorkTaskSeriesWeekdayMapper.class),
                mock(AutomationScheduleManager.class), transactions());

        assertEquals(WorkRecurrenceReadiness.DISABLED,
                repository.readiness(new WorkRecurrencePolicy(false, false, "B27A_V1", "TZDB_UNKNOWN")));
    }

    private static WorkRecurrenceControlPO control(String revision, String fingerprint) {
        WorkRecurrenceControlPO control = new WorkRecurrenceControlPO();
        control.setPolicyRevision(revision);
        control.setTzdbFingerprint(fingerprint);
        control.setReconciliationStatus("READY");
        return control;
    }

    private static WorkRecurrenceControlPO reconciling(String revision, String fingerprint, long tenantId, long seriesId) {
        WorkRecurrenceControlPO control = control(revision, fingerprint);
        control.setReconciliationStatus("RECONCILING");
        control.setTargetPolicyRevision("B27A_V2");
        control.setTargetTzdbFingerprint("TZDB_NEW");
        control.setCursorTenantId(tenantId);
        control.setCursorSeriesId(seriesId);
        return control;
    }

    private static WorkTaskSeriesPO activeSeries() {
        return activeSeries(9L);
    }

    private static WorkTaskSeriesPO activeSeries(long id) {
        WorkTaskSeriesPO series = new WorkTaskSeriesPO();
        series.setId(id);
        series.setTenantId(8L);
        series.setRecurrenceFrequency("DAILY");
        series.setRecurrenceInterval(1);
        series.setLastDayOfMonth(false);
        series.setZoneId("America/New_York");
        series.setStartLocalDate(LocalDate.of(2026, 11, 1));
        series.setLocalTime(LocalTime.of(1, 30));
        series.setStatus("ACTIVE");
        series.setNextNominalLocalTime(LocalDateTime.of(2026, 11, 1, 1, 30));
        series.setNextDueAt(LocalDateTime.of(2026, 11, 1, 6, 30));
        return series;
    }

    private static PlatformTransactionManager transactions() {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        return transactions;
    }
}
